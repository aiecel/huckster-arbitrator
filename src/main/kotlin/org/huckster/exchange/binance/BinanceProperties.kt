package org.huckster.exchange.binance

data class BinanceProperties(
    val host: String,
    val hostWebsocket: String,
    val apiKey: String,
    val apiSecret: String,
)
