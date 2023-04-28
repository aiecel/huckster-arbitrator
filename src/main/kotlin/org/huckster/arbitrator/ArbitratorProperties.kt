package org.huckster.arbitrator

/**
 * Параметры арбитратора
 */
data class ArbitratorProperties(
    val stableCoins: Set<String>, // USDT ...
    val majorCoins: Set<String>, // BTC, ETH ...
    val shitCoins: Set<String>, // щит коины
)
