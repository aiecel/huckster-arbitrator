package org.huckster.arbitrator

import mu.KotlinLogging
import org.huckster.arbitrator.model.Arbitrage
import org.huckster.arbitrator.model.ArbitrageOrder
import org.huckster.arbitrator.model.OrderType
import org.huckster.orderbook.Orderbook

/**
 * Арбитратор
 *
 * Ищет возможности для наживы (арбитража)
 */
class Arbitrator(private val properties: ArbitratorProperties) {

    private val log = KotlinLogging.logger { }

    /**
     * Получить список активов, для которых арбитратору требуются стаканы
     */
    fun generateSymbols(): Set<String> = with(properties) {
        val symbols = mutableSetOf<String>()

        stableCoins.forEach { stableCoin ->
            majorCoins.forEach { majorCoin -> symbols += majorCoin + stableCoin } // пары major-stable
            shitCoins.forEach { shitCoin -> symbols += shitCoin + stableCoin } // пары shit-stable
        }
        majorCoins.forEach { stableCoin ->
            shitCoins.forEach { majorCoin -> symbols += majorCoin + stableCoin } // пары shit-major
        }

        return symbols
    }

    /**
     * Найти возможности для арбитража среди предоставленных стаканов
     */
    fun findArbitrage(orderbooks: Map<String, Orderbook>): List<Arbitrage> {
        val arbitrageList = mutableListOf<Arbitrage>()

        forEachCoinCombination { shitCoin, majorCoin, stableCoin ->

            val shitStableSymbol = "$shitCoin$stableCoin"
            val shitMajorSymbol = "$shitCoin$majorCoin"
            val majorStableSymbol = "$majorCoin$stableCoin"

            try {
                val shitStableOrderbook = orderbooks[shitStableSymbol]
                val shitMajorOrderbook = orderbooks[shitMajorSymbol]
                val majorStableOrderbook = orderbooks[majorStableSymbol]

                requireNotNull(shitStableOrderbook)
                requireNotNull(shitMajorOrderbook)
                requireNotNull(majorStableOrderbook)

                val shitStableAsk = shitStableOrderbook.asks.lastKey()
                val shitMajorBid = shitMajorOrderbook.bids.firstKey()
                val majorStableBid = majorStableOrderbook.bids.firstKey()

                val profit = (1.0 / shitStableAsk) * shitMajorBid * majorStableBid

                if (log.isDebugEnabled) {
                    log.debug("Chain $shitStableSymbol -> $shitMajorSymbol -> $majorStableSymbol")
                    log.debug(" | $shitStableSymbol ask > ${String.format("%.8f", shitStableAsk)}")
                    log.debug(" | $shitMajorSymbol bid > ${String.format("%.8f", shitMajorBid)}")
                    log.debug(" | $majorStableSymbol bid > ${String.format("%.8f", majorStableBid)}")
                    log.debug(" | Profit: $profit")
                }

                if (profit > 1) {
                    arbitrageList += Arbitrage(
                        orders = listOf(
                            ArbitrageOrder(OrderType.BUY, shitStableSymbol, shitStableAsk),
                            ArbitrageOrder(OrderType.SELL, shitMajorSymbol, shitMajorBid),
                            ArbitrageOrder(OrderType.SELL, majorStableSymbol, majorStableBid),
                        ),
                        profit = profit
                    )
                }
            } catch (e: RuntimeException) {
                log.debug(
                    "Cannot calculate arbitrage for chain " +
                            "$shitStableSymbol -> $shitMajorSymbol -> $majorStableSymbol: " +
                            "${e.javaClass.simpleName} - ${e.message}"
                )
            }
        }

        return arbitrageList
    }

    private fun forEachCoinCombination(block: (String, String, String) -> Unit) = with(properties) {
        shitCoins.forEach { shitCoin ->
            majorCoins.forEach { majorCoin ->
                stableCoins.forEach { stableCoin ->
                    block.invoke(shitCoin, majorCoin, stableCoin)
                }
            }
        }
    }
}
