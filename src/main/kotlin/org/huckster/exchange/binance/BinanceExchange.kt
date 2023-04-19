package org.huckster.exchange.binance

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.util.*
import mu.KotlinLogging
import org.huckster.configureJacksonMapper
import org.huckster.exchange.Exchange
import org.huckster.exchange.binance.dto.ExchangeInfo
import org.huckster.exchange.binance.dto.OrderbookData
import org.huckster.exchange.binance.exception.BinanceException
import org.huckster.exchange.binance.security.LeChiffre
import org.slf4j.Logger
import java.time.Instant

private const val METHOD_PING = "/api/v3/ping"
private const val METHOD_GET_INSTRUMENTS_INFO = "api/v3/exchangeInfo"

private const val HEADER_TIMESTAMP = "X-BAPI-TIMESTAMP"
private const val HEADER_API_KEY = "X-BAPI-API-KEY"
private const val HEADER_SIGNATURE = "X-BAPI-SIGN"
private const val HEADER_RECV_WINDOW = "X-BAPI-RECV-WINDOW"

private const val DEFAULT_RECV_WINDOW = "10000"

/**
 * Биржа Буб.. Бананс
 */
class BinanceExchange(private val properties: BinanceProperties) : Exchange {

    private val log = KotlinLogging.logger { }

    // супер новомодный ktor клиент
    // https://ktor.io/docs/getting-started-ktor-client.html
    private val client = HttpClient {
        if (log.isDebugEnabled) {
            install(createLoggingPlugin(log)) // нормальные логи поставь да
        }
        install(ContentNegotiation) {
            jackson {
                configureJacksonMapper()
            }
        }
        //install(createSignaturePlugin(properties.apiSecret))
        defaultRequest {
            url {
                protocol = URLProtocol.HTTPS
                host = properties.host
            }
        }
    }

    private val websocketClient = BybitWebsocketClient(properties)

    suspend fun init() {
        log.info("Initializing Binance exchange")
        websocketClient.connect()
        log.info("Initializing Binance Exchange - done")
    }

    suspend fun listen() {
        log.info("Starting listening to Binance exchange")
        websocketClient.listen()
    }

    /**
     * Попингуй
     */
    suspend fun ping(): Long {
        val response = client.get(METHOD_PING)
        if (!response.status.isSuccess()) {
            throw BinanceException(null, null)
        }
        return response.responseTime.timestamp - response.requestTime.timestamp
    }

    /**
     * Получить все доступные символы для торговли на споте
     */
    override suspend fun getAvailableSpotSymbols(): Set<String> {
        val response: ExchangeInfo = client.get(METHOD_GET_INSTRUMENTS_INFO) {
            parameter("permissions", "SPOT")
        }.body()

        val symbols = response.symbols ?: setOf()

        return symbols.map { it.symbol }.toSet()
    }

    /**
     * Подписаться на стаканы символов
     *
     * @param symbols пары Название актива - Level (1 или 50 для spot)
     * @return id подписки
     */
    suspend fun subscribeToOrderbook(
        symbols: Set<Pair<String, Int>>,
        onMessage: (OrderbookData) -> Unit
    ) {
        val topics = symbols.map { (symbol, level) -> "orderbook.$level.$symbol" }
        websocketClient.subscribeSpot(topics, OrderbookData::class.java) {
            onMessage(it)
        }
    }

    @OptIn(InternalAPI::class)
    private fun createLoggingPlugin(log: Logger) =
        createClientPlugin("Pretty Logging Plugin") {
            client.requestPipeline.intercept(HttpRequestPipeline.Render) {
                log.debug(">>> Executing request: ${context.method.value} ${context.url}")
                if (context.method != HttpMethod.Get) {
                    log.debug(">>> ${context.body}")
                }
            }

            //client.receivePipeline.intercept(HttpReceivePipeline.After) { response ->
            //    val responseTime = response.responseTime.timestamp - response.requestTime.timestamp
            //    val body = response.content.readRemaining().readText()
            //    log.debug("<<< Received response: ${response.status} (took $responseTime ms)")
            //    if (body.isNotBlank()) {
            //        log.debug("<<< $body")
            //    }
            //}
        }


    private fun createSignaturePlugin(apiSecret: String) =
        createClientPlugin("Signature Plugin") {
            val leChiffre = LeChiffre(apiSecret)

            // подписываем все запросы
            onRequest { request, _ ->
                val timestampString = (Instant.now().epochSecond * 1000).toString()

                // если гет берём параметры, если нет - туловище
                val queryData =
                    if (request.method == HttpMethod.Get) {
                        val urlString = request.url.buildString()
                        val parametersStartIndex = urlString.indexOf('?')
                        if (parametersStartIndex > 0) urlString.substring(parametersStartIndex) else ""
                    } else {
                        request.body
                    }

                // так надо
                val data = timestampString + properties.apiKey + DEFAULT_RECV_WINDOW + queryData.toString()

                // подпись
                val signature = leChiffre.generate(data)

                request.headers {
                    append(HEADER_TIMESTAMP, timestampString)
                    append(HEADER_SIGNATURE, signature)
                }
            }
        }
}
