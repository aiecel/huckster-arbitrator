package org.huckster

import org.huckster.arbitrage.model.Order
import org.huckster.exchange.binance.model.ExchangeInfoResponse

data class ArbitrageStop(
    val symbol: ExchangeInfoResponse.Symbol,
    val orders: List<Order>,
) {

}
