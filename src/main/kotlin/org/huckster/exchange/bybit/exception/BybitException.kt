package org.huckster.exchange.bybit.exception

class BybitException(code: Int?, message: String?) : RuntimeException("$code : $message")
