package org.huckster.orderbook

import java.time.Instant

/**
 * Изменение стакана
 */
data class OrderbookDiff(
    val timestamp: Instant = Instant.now(),
    val newAsks: Map<Double, Double> = mapOf(),
    val newBids: Map<Double, Double> = mapOf(),
)
