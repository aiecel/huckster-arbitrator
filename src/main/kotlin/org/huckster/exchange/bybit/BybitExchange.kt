package org.huckster.exchange.bybit

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
import org.huckster.exchange.bybit.dto.ApiKeyInfo
import org.huckster.exchange.bybit.dto.InstrumentsInfo
import org.huckster.exchange.bybit.dto.OrderbookData
import org.huckster.exchange.bybit.security.LeChiffre
import org.slf4j.Logger
import java.time.Instant

private const val METHOD_GET_API_KEY_INFO = "/v5/user/query-api"
private const val METHOD_GET_INSTRUMENTS_INFO = "/v5/market/instruments-info"

private const val HEADER_TIMESTAMP = "X-BAPI-TIMESTAMP"
private const val HEADER_API_KEY = "X-BAPI-API-KEY"
private const val HEADER_SIGNATURE = "X-BAPI-SIGN"
private const val HEADER_RECV_WINDOW = "X-BAPI-RECV-WINDOW"

private const val DEFAULT_RECV_WINDOW = "10000"

/**
 * Биржа Бубит
 */
class BybitExchange(private val properties: BybitProperties) : Exchange {

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
        install(createBybitSignaturePlugin(properties.apiSecret))
        defaultRequest {
            url {
                protocol = URLProtocol.HTTP
                host = properties.host
            }
            header(HEADER_API_KEY, properties.apiKey)
            header(HEADER_RECV_WINDOW, DEFAULT_RECV_WINDOW)
        }
    }

    private val websocketClient = BybitWebsocketClient(properties)

    suspend fun init() {
        log.info("Initializing Bybit exchange")
        websocketClient.connect()
        log.info("Initializing Bybit Exchange - done")
    }

    suspend fun listen() {
        log.info("Starting listening to Bybit exchange")
        websocketClient.listen()
    }

    /**
     * Получить информацию об API ключе
     *
     * [Документация](https://bybit-exchange.github.io/docs/v5/user/apikey-info)
     */
    suspend fun getApiKeyInfo(): ApiKeyInfo {
        return client.get(METHOD_GET_API_KEY_INFO).body()
    }

    /**
     * Получить все доступные символы для торговли на споте
     */
    override suspend fun getAvailableSpotSymbols(): Set<String> {
        val response: InstrumentsInfo = client.get(METHOD_GET_INSTRUMENTS_INFO) {
            parameter("category", "spot")
        }.body()

        return response.result?.list?.map { it.symbol }?.toSet() ?: setOf()
    }

    /**
     * Подписаться на стаканы символов
     *
     * [Документация](https://bybit-exchange.github.io/docs/v5/websocket/public/orderbook)
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

            client.responsePipeline.intercept(HttpResponsePipeline.Receive) {
                val response = context.response
                val responseTime = response.responseTime.timestamp - response.requestTime.timestamp
                val body = response.content.readRemaining().readText()
                log.debug("<<< Received response: ${response.status} (took $responseTime ms)")
                if (body.isNotBlank()) {
                    log.debug("<<< ${response.content.readRemaining().readText()}")
                }
            }
        }

    private fun createBybitSignaturePlugin(apiSecret: String) =
        createClientPlugin("Bybit Signature Plugin") {
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
