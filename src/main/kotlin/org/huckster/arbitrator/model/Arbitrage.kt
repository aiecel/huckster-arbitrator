package org.huckster.arbitrator.model

import java.util.*

data class Arbitrage(
    val id: UUID = UUID.randomUUID(),
    val orders: List<ArbitrageOrder>,
    val profit: Double
)
