package org.huckster.exchange.binance

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mu.KotlinLogging
import org.huckster.util.configureJacksonMapper
import org.huckster.exchange.Exchange
import org.huckster.exchange.binance.exception.BinanceException
import org.huckster.exchange.binance.model.DiffDepthStreamMessage
import org.huckster.exchange.binance.model.ExchangeInfoResponse
import org.huckster.orderbook.model.OrderbookDiff
import org.huckster.util.loggingPlugin
import java.time.Instant

/**
 * Биржа Буб.. Бананс
 *
 * [Документация](https://binance-docs.github.io/apidocs/spot/en/#introduction)
 */
class BinanceExchange(private val properties: BinanceExchangeProperties) : Exchange {

    /**
     * Получить процент комиссии на бирже
     */
    override val feePercentage = properties.feePercentage

    private val log = KotlinLogging.logger { }

    // супер новомодный ktor клиент
    // https://ktor.io/docs/getting-started-ktor-client.html
    private val client = HttpClient {
        if (log.isDebugEnabled) {
            install(loggingPlugin(log)) // нормальные логи поставь да
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

    private val websocketClient = BinanceWebsocketClient(properties)

    /**
     * Попингуй
     */
    suspend fun ping(): Long {
        val response = client.get("/api/v3/ping")
        if (!response.status.isSuccess()) {
            throw BinanceException(null, null)
        }
        return response.responseTime.timestamp - response.requestTime.timestamp
    }

    /**
     * Получить все доступные символы для торговли на споте
     */
    override suspend fun getAvailableSpotSymbols(): Set<String> {
        val response = client
            .get("api/v3/exchangeInfo") { parameter("permissions", "SPOT") }
            .body<ExchangeInfoResponse>()

        val symbols = response.symbols
        return symbols.map { it.symbol }.toSet()
    }

    /**
     * Подписаться на изменения стаканов
     *
     * @param symbols Символы
     * @return пары Символ - Изменение стакана
     */
    override suspend fun listenToOrderbookDiff(symbols: Set<String>): Flow<Pair<String, OrderbookDiff>> {
        val streams = symbols.map { symbol -> "${symbol.lowercase()}@depth@100ms" }

        return websocketClient
            .listen(streams, DiffDepthStreamMessage::class.java)
            .map { message ->
                val symbol = message.stream.substringBefore("@").uppercase()

                with(message.data) {
                    val orderbookDiff = OrderbookDiff(
                        timestamp = Instant.ofEpochMilli(timestamp),
                        newAsks = asks.associate { ask -> ask[0].toDouble() to ask[1].toDouble() },
                        newBids = bids.associate { bid -> bid[0].toDouble() to bid[1].toDouble() }
                    )
                    symbol to orderbookDiff
                }
            }
    }
}
