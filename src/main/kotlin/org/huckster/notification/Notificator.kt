package org.huckster.notification

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging

class Notificator(private val properties: NotificationProperties) {

    private val client = HttpClient {
        defaultRequest {
            url {
                protocol = URLProtocol.HTTPS
                host = properties.host
            }
        }
        expectSuccess = true
    }

    private val log = KotlinLogging.logger { }

    suspend fun sendNotification(text: String) {
        client.post("/bot${properties.token}/sendMessage") {
            parameter("chat_id", properties.chatId)
            parameter("text", text)
            parameter("parse_mode", "MarkdownV2")
        }

        log.info("Notification sent: ${text.replace("\n", "|")}")
    }
}
