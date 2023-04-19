package org.huckster.exchange

import org.huckster.exchange.bybit.BybitProperties

/**
 * Параметры бирж
 */
data class ExchangeProperties(
    val bybit: BybitProperties
)
