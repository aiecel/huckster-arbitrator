package org.huckster.arbitrator.model

import java.util.*
import kotlin.math.roundToInt

/**
 * Арбитраж
 */
data class Arbitrage(

    /**
     * ID арбитража
     */
    val id: UUID = UUID.randomUUID(),

    /**
     * Сделки, которые нужно совершить, чтобы заработать бабло (в теории)
     */
    val orders: List<Order>,

    /**
     * Прогнозируемый профит (>1)
     */
    val profit: Double
) {

    /**
     * Получить прогнозируемый профит в процентах
     */
    fun profitPercentage() = ((profit - 1) * 10000).roundToInt() / 100.0
}
