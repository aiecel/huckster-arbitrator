package org.huckster.orderbook

import mu.KotlinLogging
import org.huckster.orderbook.model.OrderbookDiff
import java.util.concurrent.ConcurrentHashMap

/**
 * Шкаф со стаканами
 */
class OrderbookKeeper(private val orderbookProperties: OrderbookProperties) {

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
            orderbooks[symbol] = Orderbook(orderbookProperties.orderbookSize)
        }
    }

    /**
     * Обновить стакан для актива
     */
    fun updateOrderbook(symbol: String, orderbookDiff: OrderbookDiff) {
        val orderbook = orderbooks[symbol] ?: return
        orderbook.update(orderbookDiff)
        log.debug(
            "Updated orderbook for symbol $symbol " +
                    "with ${orderbookDiff.newAsks.size} asks and ${orderbookDiff.newBids.size} bids"
        )
        logOrderbook(symbol, orderbook)
    }

    /**
     * Очистить все стаканы
     */
    fun clearAllOrderbooks() = orderbooks.forEach { (_, orderbook) -> orderbook.clear() }

    private fun logOrderbook(symbol: String, orderbook: Orderbook) {
        if (log.isDebugEnabled) {
            log.debug("Orderbook for symbol $symbol:")
            orderbook.asks.forEach { (price, value) ->
                log.debug("| $price\t$value")
            }
            log.debug("| ----------")
            orderbook.bids.forEach { (price, value) ->
                log.debug("| $price\t$value")
            }
        }
    }
}
