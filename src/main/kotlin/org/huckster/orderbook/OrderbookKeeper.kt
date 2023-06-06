package org.huckster.orderbook

import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

/**
 * Шкаф со стаканами
 */
class OrderbookKeeper(

    /**
     * Размер стаканов, которые будут стоять в шкафчике
     */
    private val orderbookSize: Int? = null
) {

    private val orderbooks = ConcurrentHashMap<String, Orderbook>()
    private val log = KotlinLogging.logger { }

    /**
     * Получить стакан по символу актива
     */
    fun getOrderbook(symbol: String): Orderbook? = orderbooks[symbol]

    /**
     * Создать стаканы для активов
     */
    fun createOrderbooks(symbols: Iterable<String>) {
        symbols.forEach { symbol ->
            orderbooks[symbol] = if (orderbookSize != null) Orderbook(orderbookSize) else Orderbook()
        }
    }

    /**
     * Обновить стакан для актива
     */
    fun updateOrderbook(symbol: String, orderbookDiff: OrderbookDiff) {
        orderbooks[symbol]?.update(orderbookDiff)
        log.debug(
            "Updated orderbook for symbol $symbol " +
                    "with ${orderbookDiff.newAsks.size} asks and ${orderbookDiff.newBids.size} bids"
        )
    }
}
