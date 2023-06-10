package org.huckster.arbitrage.model

import java.time.LocalDateTime
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
     * Время появления арбитража
     */
    val timestamp: LocalDateTime = LocalDateTime.now(),

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
    fun profitPercentage() = ((profit - 1) * 100000).roundToInt() / 1000.0
}
