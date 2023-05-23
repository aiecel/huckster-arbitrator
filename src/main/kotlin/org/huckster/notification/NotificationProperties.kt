package org.huckster.notification

data class NotificationProperties(
    val host: String,
    val token: String,
    val chatId: String
)
