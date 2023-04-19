package org.huckster.exchange.binance.dto

data class ExchangeInfo(val symbols: List<Symbol>?) {

    data class Symbol(
        val symbol: String
    )
}
