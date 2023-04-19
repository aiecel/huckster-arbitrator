package org.huckster.exchange.binance.exception

class BinanceException(code: Int?, message: String?) : RuntimeException("$code : $message")
