package org.huckster.arbitrator.model

/**
 * Сделка
 */
data class Order(
    val type: OrderType,
    val symbol: String,
    val price: Double
)
