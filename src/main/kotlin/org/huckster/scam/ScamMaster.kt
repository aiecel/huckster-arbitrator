package org.huckster.scam

import mu.KotlinLogging
import org.huckster.arbitrator.model.Arbitrage
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

        // проверка на прибыльность
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

            // проверка на свежесть стакана
            val orderbookFreshnessMinutes =
                Duration
                    .between(orderbook.lastUpdatedTimestamp, Instant.now())
                    .toMinutes()

            if (orderbookFreshnessMinutes > properties.orderbookFreshnessMinutes) {
                log.info(
                    "Arbitrage ${arbitrage.id} is a scam: " +
                            "orderbook ${order.symbol} is too old (updated $orderbookFreshnessMinutes minutes ago)"
                )
                return false
            }
        }

        return true
    }
}
