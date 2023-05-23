package org.huckster.exchange

import org.huckster.exchange.binance.BinanceExchangeProperties

/**
 * Параметры бирж
 */
data class ExchangeProperties(
    val binance: BinanceExchangeProperties
)
