package org.huckster.scam

/**
 * Параметры скам мастера
 */
data class ScamProperties(

    /**
     * Минимальный прогнозируемый профит
     */
    val minArbitrageProfitPercentage: Double = 3.0,

    /**
     * Сколько минут должно пройти с последнего обновления стакана, чтобы он считался тухлым
     */
    val orderbookFreshnessMinutes: Long = 5
)
