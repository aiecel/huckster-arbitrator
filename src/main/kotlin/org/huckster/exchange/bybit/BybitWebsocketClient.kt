package org.huckster.exchange.bybit

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import mu.KotlinLogging
import org.huckster.configureJacksonMapper
import org.huckster.exchange.bybit.dto.PublicWebsocketMessage
import org.huckster.exchange.bybit.dto.SubscribeRequest
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

private const val PROTOCOL = "wss://"

private const val CHANNEL_PUBLIC_SPOT = "/v5/public/spot"

/**
 * Websocket клиент Бубита
 *
 * (осторожно! Присутствуют костыли)
 */
class BybitWebsocketClient(private val properties: BybitProperties) {

    private val objectMapper = ObjectMapper().configureJacksonMapper()
    private val log = KotlinLogging.logger { }

    private val client = HttpClient {
        install(WebSockets)
    }

    private var spotChannelSession: DefaultClientWebSocketSession? = null

    private val requestSubscriptions: ConcurrentMap<String, (String) -> Unit> = ConcurrentHashMap()
    private val topicSubscriptions: ConcurrentMap<String, (String) -> Unit> = ConcurrentHashMap()

    suspend fun connect() {
        val url = "$PROTOCOL${properties.hostWebsocket}$CHANNEL_PUBLIC_SPOT"

        log.info("Connecting to spot channel: $url")
        spotChannelSession = client.webSocketSession(url)
        log.info("Connecting to spot channel - done")
    }

    suspend fun listen() {
        log.info("Starting listening to spot channel")
        listenToMessages(spotChannelSession) { receivedMessage ->
            onMessage(receivedMessage)
        }
    }

    /**
     * Подписаться на топики (канал spot)
     */
    @Suppress("BlockingMethodInNonBlockingContext") // гонит порожняк
    suspend fun <T> subscribeSpot(
        topics: List<String>,
        responseClass: Class<T>,
        onMessage: (T) -> Unit
    ) {
        requireNotNull(spotChannelSession) { "Websocket channel session must be initialized" }

        val requestId = UUID.randomUUID().toString()
        val request = SubscribeRequest(requestId = requestId, args = topics)
        val requestString = objectMapper.writeValueAsString(request)

        log.debug(">>> Sending WS request $requestString")

        spotChannelSession!!.send(requestString)

        // сохраняем тип чтоб потом знать его в topicSubscription
        val responseType = objectMapper.constructType(responseClass)

        requestSubscriptions[requestId] = {
            requestSubscriptions -= requestId
            topics.forEach { topic ->
                topicSubscriptions[topic] = { message ->
                    onMessage(objectMapper.readValue(message, responseType))
                }
            }
            log.debug("Added subscriptions to ${topics.size} topics")
        }
    }

    private suspend fun listenToMessages(session: DefaultClientWebSocketSession?, onMessage: (String) -> Unit) {
        requireNotNull(session) { "Websocket channel session must be initialized" }
        while (true) {
            val message = (session.incoming.receive() as? Frame.Text)?.readText()
            if (message != null) {
                onMessage(message)
            }
        }
    }

    private fun onMessage(message: String) {
        val messageObject: PublicWebsocketMessage = objectMapper.readValue(message)

        if (messageObject.topic != null) {
            log.debug("<<< Received WS message (topic): $message")
            topicSubscriptions[messageObject.topic]?.invoke(message)
        } else if (messageObject.requestId != null) {
            log.debug("<<< Received WS message (response): $message")
            requestSubscriptions[messageObject.requestId]?.invoke(message)
        } else {
            log.warn("<<< Received WS message (unknown): $message")
        }
    }
}
