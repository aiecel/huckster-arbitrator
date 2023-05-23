package org.huckster.exchange.binance

data class BinanceExchangeProperties(
    val host: String,
    val hostWebsocket: String,
    val apiKey: String,
    val apiSecret: String,
)
