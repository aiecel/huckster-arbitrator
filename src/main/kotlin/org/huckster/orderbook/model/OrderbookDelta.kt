package org.huckster.orderbook.model

import java.time.Instant

/**
 * Изменение стакана
 */
data class OrderbookDelta(
    val timestamp: Instant = Instant.now(),
    val newAsks: Map<Double, Double> = mapOf(),
    val newBids: Map<Double, Double> = mapOf(),
)
