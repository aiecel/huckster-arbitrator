package org.huckster.exchange.binance

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import mu.KotlinLogging
import org.huckster.util.configureJacksonMapper
import org.huckster.util.loggingPlugin

/**
 * Websocket клиент Бинанса
 *
 * [Документация](https://binance-docs.github.io/apidocs/spot/en/#websocket-market-streams)
 */
class BinanceWebsocketClient(private val properties: BinanceExchangeProperties) {

    private val objectMapper = ObjectMapper().configureJacksonMapper()
    private val log = KotlinLogging.logger { }

    private val client = HttpClient {
        install(WebSockets)
        install(loggingPlugin(log))
    }

    /**
     * Подписаться на streams
     */
    @Suppress("BlockingMethodInNonBlockingContext") // майкл джексон гонит порожняк
    suspend fun <T> listen(streams: Collection<String>, responseClass: Class<T>): Flow<T> = flow {
        val baseUrl = URLBuilder(protocol = URLProtocol.WSS, host = properties.hostWebsocket).buildString()
        val streamsQuery = streams.joinToString("/")
        val fullUrl = "$baseUrl/stream?streams=$streamsQuery"

        log.info("Connecting to ${streams.size} streams...")

        client.webSocket(fullUrl) {
            log.info("Listening to ${streams.size} streams")

            while (isActive) {
                val incomingFrame = incoming.receive() as? Frame.Text
                val incomingMessage = incomingFrame?.readText()

                if (incomingMessage == null) {
                    log.debug("< Received null WS message :|")
                } else try {
                    log.debug("< Received WS message: $incomingMessage")
                    val response = objectMapper.readValue(incomingMessage, responseClass)
                    emit(response)
                } catch (e: JacksonException) {
                    log.warn(
                        "Cannot parse WS message to type ${responseClass.simpleName} - ${e.message}, " +
                                "message text: $incomingMessage"
                    )
                }
            }
            close()
        }
    }
}
