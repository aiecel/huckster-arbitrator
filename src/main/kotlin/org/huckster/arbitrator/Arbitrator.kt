package org.huckster.arbitrator

import mu.KotlinLogging
import org.huckster.arbitrator.model.Arbitrage
import org.huckster.arbitrator.model.Order
import org.huckster.arbitrator.model.OrderType
import org.huckster.exchange.binance.BinanceExchange
import org.huckster.orderbook.OrderbookKeeper

/**
 * Арбитратор
 *
 * Ищет возможности для наживы (арбитража)
 *
 * (Алгоритм Кузнецова А.Е.)
 */
class Arbitrator(
    private val properties: ArbitratorProperties,
    private val orderbookKeeper: OrderbookKeeper,
    private val exchange: BinanceExchange,
) {

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
        majorCoins.forEach { majorCoin ->
            shitCoins.forEach { shitCoin -> symbols += shitCoin + majorCoin } // пары shit-major
        }

        return symbols
    }

    /**
     * Найти возможности для арбитража среди предоставленных стаканов
     */
    fun findAllArbitrages(): List<Arbitrage> {
        val arbitrageList = mutableListOf<Arbitrage>()

        forEachCoinCombination { shitCoin, majorCoin, stableCoin ->
            val arbitrage = findArbitrage(shitCoin, majorCoin, stableCoin)
            if (arbitrage != null) {
                arbitrageList += arbitrage
            }
        }

        return arbitrageList
    }

    private fun findArbitrage(shitCoin: String, majorCoin: String, stableCoin: String): Arbitrage? {
        val shitStableSymbol = "$shitCoin$stableCoin"
        val shitMajorSymbol = "$shitCoin$majorCoin"
        val majorStableSymbol = "$majorCoin$stableCoin"

        val shitStableOrderbook = orderbookKeeper.getOrderbook(shitStableSymbol)
        val shitMajorOrderbook = orderbookKeeper.getOrderbook(shitMajorSymbol)
        val majorStableOrderbook = orderbookKeeper.getOrderbook(majorStableSymbol)

        if (shitStableOrderbook == null || shitMajorOrderbook == null || majorStableOrderbook == null) {
            log.debug(
                "Cannot find arbitrage " +
                        "for chain $shitStableSymbol -> $shitMajorSymbol -> $majorStableSymbol - " +
                        "some orderbooks are missing"
            )
            return null
        }

        if (shitStableOrderbook.asks.isEmpty()
            || shitMajorOrderbook.bids.isEmpty()
            || majorStableOrderbook.bids.isEmpty()
        ) {
            log.debug(
                "Cannot find arbitrage " +
                        "for chain $shitStableSymbol -> $shitMajorSymbol -> $majorStableSymbol - " +
                        "some orderbooks are missing prices needed"
            )
            return null
        }

        val shitStableAsk = shitStableOrderbook.asks.lastKey()
        val shitMajorBid = shitMajorOrderbook.bids.firstKey()
        val majorStableBid = majorStableOrderbook.bids.firstKey()

        val fee = exchange.feePercentage / 100

        val shitStableAskWithFee = shitStableAsk * (1 + fee)
        val shitMajorBidWithFee = shitMajorBid * (1 - fee)
        val majorStableBidWithFee = majorStableBid * (1 - fee)

        val profit = (1 / shitStableAskWithFee) * shitMajorBidWithFee * majorStableBidWithFee

        if (log.isTraceEnabled) {
            log.trace("Chain $shitStableSymbol -> $shitMajorSymbol -> $majorStableSymbol")
            log.trace("| $shitStableSymbol ask > ${shitStableAsk.formatPrice()}")
            log.trace("| $shitMajorSymbol bid > ${shitMajorBid.formatPrice()}")
            log.trace("| $majorStableSymbol bid > ${majorStableBid.formatPrice()}")
            log.trace("| Profit: $profit")
        }

        return if (profit > 1) {
            Arbitrage(
                orders = listOf(
                    Order(OrderType.BUY, shitStableSymbol, shitStableAsk),
                    Order(OrderType.SELL, shitMajorSymbol, shitMajorBid),
                    Order(OrderType.SELL, majorStableSymbol, majorStableBid),
                ),
                profit = profit
            )
        } else null
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

    private fun Double.formatPrice(): String = String.format("%.6f", this)
}
