package org.huckster.arbitrator

import org.huckster.arbitrator.properties.ArbitratorProperties

/**
 * Арбитратор
 *
 * Ищет возможности для наживы (арбитража)
 */
class Arbitrator(private val properties: ArbitratorProperties) {

    /**
     * Получить список активов, для которых арбитратору требуются стаканы
     */
    fun generateSymbols(): Set<String> {
        val symbols = mutableSetOf<String>()

        properties.stableCoins.forEach { stableCoin ->

            // пары major-stable
            properties.majorCoins.forEach { majorCoin ->
                symbols += majorCoin + stableCoin
            }

            // пары shit-stable
            properties.shitCoins.forEach { shitCoin ->
                symbols += shitCoin + stableCoin
            }
        }

        properties.majorCoins.forEach { stableCoin ->

            // пары shit-major
            properties.shitCoins.forEach { majorCoin ->
                symbols += majorCoin + stableCoin
            }
        }

        return symbols
    }
}
