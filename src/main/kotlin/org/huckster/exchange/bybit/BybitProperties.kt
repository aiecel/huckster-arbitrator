package org.huckster.exchange.bybit

data class BybitProperties(
    val host: String,
    val hostWebsocket: String,
    val apiKey: String,
    val apiSecret: String,
)
