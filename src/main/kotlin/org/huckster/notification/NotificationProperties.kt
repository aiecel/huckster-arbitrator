package org.huckster.notification

/**
 * Параметры телеграм уведомлений
 */
data class NotificationProperties(
    val host: String = "api.telegram.org",
    val token: String,
    val chatId: String, // смотри в url в телеге
    val testMode: Boolean = false, // отправлять ли уведомления
)
