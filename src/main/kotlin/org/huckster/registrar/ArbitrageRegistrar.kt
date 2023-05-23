package org.huckster.registrar

import mu.KotlinLogging
import org.huckster.arbitrator.model.Arbitrage
import org.huckster.notification.Notificator
import org.huckster.storage.Storage
import kotlin.math.roundToInt

class ArbitrageRegistrar(
    private val properties: ArbitrageRegistrarProperties,
    private val notificator: Notificator,
    private val storage: Storage
) {
    private val log = KotlinLogging.logger { }

    suspend fun registerArbitrageFound(arbitrage: Arbitrage) {
        log.info("Found arbitrage: profit ${arbitrage.profit.asProfitPercentage()}%")
        arbitrage.orders.forEach { log.info("- ${it.type} ${it.symbol} for ${it.price}") }

        if (properties.sendNotifications) {
            try {
                sendArbitrageFoundNotification(arbitrage)
            } catch (e: RuntimeException) {
                log.warn("Cannot send notification about found arbitrage ${arbitrage.id}: ${e.message}")
            }
        }

        if (properties.useStorage) {
            try {
                storage.save(arbitrage)
            } catch (e: RuntimeException) {
                log.warn("Cannot store found arbitrage ${arbitrage.id}: ${e.message}")
            }
        }
    }

    private suspend fun sendArbitrageFoundNotification(arbitrage: Arbitrage) = with(arbitrage) {
        val profitPercentage = profit.asProfitPercentage()

        val notificationText = StringBuilder("Найден арбитраж: профит $profitPercentage%")
        arbitrage.orders.forEachIndexed { i, order ->
            notificationText.append("\n ${i + 1}) ${order.type} ${order.symbol} за ${order.price}\"")
        }

        notificator.sendNotification(notificationText.toString())
    }

    private fun Double.asProfitPercentage() = ((this - 1) * 10000).roundToInt() / 100.0
}
