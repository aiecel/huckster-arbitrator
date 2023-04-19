package org.huckster.properties

import org.huckster.arbitrator.properties.ArbitratorProperties
import org.huckster.exchange.ExchangeProperties

/**
 * Параметры (корень)
 */
data class ApplicationProperties(
    val exchange: ExchangeProperties,
    val arbitrator: ArbitratorProperties,
)
