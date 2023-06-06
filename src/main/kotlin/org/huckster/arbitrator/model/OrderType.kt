package org.huckster.arbitrator.model

/**
 * Тип сделки
 */
enum class OrderType(val description: String) {
    BUY("Покупка"),
    SELL("Продажа")
}
