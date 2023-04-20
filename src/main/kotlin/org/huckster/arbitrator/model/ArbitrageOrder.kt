package org.huckster.arbitrator.model

data class ArbitrageOrder(
    val type: OrderType,
    val symbol: String,
)
