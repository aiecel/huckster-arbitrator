package org.huckster.notification

import org.huckster.arbitrage.model.Arbitrage

fun applicationShutdown(message: String?) = NotificationBuilder()
    .bold("\uD83E\uDEE1 Huckster выключается... \n\n")
    .text("Код выключения: ")
    .code(message ?: "неизвестен")

fun arbitrageFound(arbitrage: Arbitrage) = with(NotificationBuilder()) {
    bold("\uD83D\uDCB5 Найден арбитраж: профит ${arbitrage.profitPercentage()}%\n\n")
    arbitrage.orders.forEachIndexed { i, order ->
        text("${i + 1}) ${order.type.description} ${order.symbol} за ${order.price}\n")
    }
    text("\n")
    code("${arbitrage.id}")
}

fun orderbookUpdateFailure(exception: Exception) = NotificationBuilder()
    .bold("\uD83E\uDEE3 Произошла ФАТАЛЬНАЯ ошибка в процессе обновления стаканов!\n\n")
    .text("Код ошибки: ")
    .code("${exception.javaClass.simpleName}: ${exception.message}")
