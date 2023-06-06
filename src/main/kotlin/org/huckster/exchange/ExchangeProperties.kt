package org.huckster.exchange

import org.huckster.exchange.binance.BinanceExchangeProperties

/**
 * Параметры бирж
 */
data class ExchangeProperties(

    /**
     * Параметры Бинанса
     */
    val binance: BinanceExchangeProperties
)
