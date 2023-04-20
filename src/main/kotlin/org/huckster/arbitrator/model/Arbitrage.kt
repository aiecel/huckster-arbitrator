package org.huckster.arbitrator.model

data class Arbitrage(
    val orders: List<ArbitrageOrder>,
    val profit: Double
)
