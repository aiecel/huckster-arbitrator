package org.huckster.orderbook

data class OrderbookDiff(
    val timestamp: Long,
    val newAsks: Map<Double, Double>,
    val newBids: Map<Double, Double>,
)
