package org.huckster.notification

class NotificationException(override val message: String?, override val cause: Throwable?) : RuntimeException()