package org.huckster.exchange.binance.model

data class ExchangeInfoResponse(val symbols: List<Symbol>) {
    data class Symbol(val symbol: String)
}
