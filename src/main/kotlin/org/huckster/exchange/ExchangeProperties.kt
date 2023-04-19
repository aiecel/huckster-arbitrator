package org.huckster.exchange

import org.huckster.exchange.binance.BinanceProperties

/**
 * Параметры бирж
 */
data class ExchangeProperties(
    val binance: BinanceProperties
)
