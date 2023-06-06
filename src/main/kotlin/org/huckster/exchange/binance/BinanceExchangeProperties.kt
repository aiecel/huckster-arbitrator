package org.huckster.exchange.binance

/**
 * Параметры Бинанса
 */
data class BinanceExchangeProperties(
    val host: String = "api.binance.com",
    val hostWebsocket: String = "stream.binance.com:9443",
    val apiKey: String,
    val apiSecret: String,
    val feePercentage: Double = 0.1
)
