package org.huckster.arbitrage.model

/**
 * Сделка
 */
data class Order(
    val type: OrderType,
    val symbol: String,
    val price: Double
)
