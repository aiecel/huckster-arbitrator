package org.huckster.scam

import org.huckster.arbitrator.model.Arbitrage

/**
 * Скам мастер
 */
class ScamMaster(private val properties: ScamProperties) {

    /**
     * Одобрить (или не одобрить) сделку
     */
    fun isApproved(arbitrage: Arbitrage): Boolean =
        arbitrage.profit > properties.minArbitrageProfit
}
