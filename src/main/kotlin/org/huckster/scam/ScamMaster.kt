package org.huckster.scam

import mu.KotlinLogging
import org.huckster.arbitrage.model.Arbitrage
import org.huckster.orderbook.Orderbook
import org.huckster.orderbook.OrderbookKeeper
import java.time.Duration
import java.time.Instant

/**
 * Скам мастер
 */
class ScamMaster(
    private val properties: ScamProperties,
    private val orderbookKeeper: OrderbookKeeper
) {

    private val log = KotlinLogging.logger { }

    /**
     * Одобрить (или не одобрить) сделку
     */
    fun isApproved(arbitrage: Arbitrage): Boolean {
        log.info("Examining arbitrage ${arbitrage.id}")

        // проверка на прибыльность
        log.info("Estimated profit: ${arbitrage.profit}%")
        if (arbitrage.profitPercentage() < properties.minArbitrageProfitPercentage) {
            log.info("Arbitrage ${arbitrage.id} is a scam: profit is too low (${arbitrage.profit}%)")
            return false
        }

        arbitrage.orders.forEach { order ->
            val orderbook = orderbookKeeper.getOrderbook(order.symbol)
            if (orderbook == null) {
                log.warn("Arbitrage ${arbitrage.id} is a scam: orderbook ${order.symbol} not found somehow")
                return false
            }

            log.info("Orderbook for ${order.symbol} - last updated at ${orderbook.lastUpdatedTimestamp}")
            logOrderbook(order.symbol, orderbook)

            // проверка на свежесть стакана
            val orderbookAge = Duration.between(orderbook.lastUpdatedTimestamp, Instant.now())

            log.info("Orderbook age: $orderbookAge")
            if (orderbookAge.toMinutes() > properties.orderbookMaxAgeMinutes) {
                log.info(
                    "Arbitrage ${arbitrage.id} is a scam: " +
                            "orderbook ${order.symbol} is too old"
                )
                return false
            }
        }

        log.info("Arbitrage ${arbitrage.id} seems legit!")
        return true
    }

    private fun logOrderbook(symbol: String, orderbook: Orderbook) {
        log.info("Orderbook for symbol $symbol:")
        orderbook.asks.forEach { (price, value) ->
            log.info("| $price\t$value")
        }
        log.info("| ----------")
        orderbook.bids.forEach { (price, value) ->
            log.info("| $price\t$value")
        }
    }
}
