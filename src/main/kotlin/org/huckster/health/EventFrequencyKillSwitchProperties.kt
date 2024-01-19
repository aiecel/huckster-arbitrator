package org.huckster.health

import java.time.temporal.ChronoUnit

/**
 * Параметры рубильника на частоту событий
 */
data class EventFrequencyKillSwitchProperties(

    /**
     * Кол-во времени
     */
    val timeWindowAmount: Long = 1,

    /**
     * Кол-во времени (время измерения)
     */
    val timeWindowUnit: ChronoUnit = ChronoUnit.HOURS,

    /**
     * Максимальное количество событий в указанный период
     *
     * -1 значит можно хоть сколько
     */
    val maxEventsInTimeWindow: Int = -1
)
