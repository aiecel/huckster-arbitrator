package org.huckster.arbitrage

/**
 * Параметры арбитратора
 */
data class ArbitratorProperties(
    val stableCoins: Set<String> = setOf(), // USDT ...
    val majorCoins: Set<String> = setOf(), // BTC, ETH ...
    val shitCoins: Set<String> = setOf(), // щит коины
)
