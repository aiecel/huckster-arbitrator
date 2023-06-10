package org.huckster.orderbook

/**
 * Настройки хранилища стаканов
 */
data class OrderbookProperties(

    /**
     * Размер стаканов
     */
    val orderbookSize: Int = 10
)
