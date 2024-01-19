package org.huckster.scam

/**
 * Параметры скам мастера
 */
data class ScamProperties(

    /**
     * Минимальный прогнозируемый профит
     */
    val minArbitrageProfitPercentage: Double = 1.0,

    /**
     * Сколько минут должно пройти с последнего обновления стакана, чтобы он считался тухлым
     */
    val orderbookMaxAgeMinutes: Long = 3
)
