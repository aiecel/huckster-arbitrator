package org.huckster.notification

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import mu.KotlinLogging
import org.huckster.util.loggingPlugin

/**
 * Телеграм уведомлятор
 *
 * [Документация](https://core.telegram.org/bots/api#sendmessage)
 */
class Notificator(private val properties: NotificationProperties) {

    private val log = KotlinLogging.logger { }

    private val client = HttpClient {
        defaultRequest {
            url {
                protocol = URLProtocol.HTTPS
                host = properties.host
            }
        }
        if (log.isDebugEnabled) {
            install(loggingPlugin(log)) // нормальные логи поставь да
        }
        expectSuccess = true
    }

    /**
     * Отправить уведомление (очень круто)
     */
    suspend fun sendNotification(block: NotificationBuilder.() -> Unit) {
        val builder = NotificationBuilder()
        builder.block()
        sendNotification(builder.toString())
    }

    /**
     * Отправить уведомление
     */
    suspend fun sendNotification(builder: NotificationBuilder) {
        sendNotification(builder.toString())
    }

    private suspend fun sendNotification(text: String) {
        try {
            val inlineNotificationText = text.replace("\n", "|")

            log.info("Sending notification: $inlineNotificationText")

            if (!properties.testMode) {
                client.post("/bot${properties.token}/sendMessage") {
                    parameter("chat_id", properties.chatId)
                    parameter("text", text)
                    parameter("parse_mode", "MarkdownV2")
                }
            }

            log.info("Notification sent: $inlineNotificationText")
        } catch (exception: ResponseException) {
            with(exception.response) {
                throw NotificationException(
                    "$status from ${request.method} ${request.url}: ${bodyAsText()}",
                    exception
                )
            }
        } catch (exception: RuntimeException) {
            throw NotificationException("${exception.javaClass.simpleName}: ${exception.message}", exception)
        }
    }
}
