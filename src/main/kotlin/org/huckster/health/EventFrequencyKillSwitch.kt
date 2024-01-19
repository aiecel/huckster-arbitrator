package org.huckster.health

import java.time.Duration
import java.time.LocalDateTime

/**
 * Рубильник на частоту событий
 */
class EventFrequencyKillSwitch(private val properties: EventFrequencyKillSwitchProperties) {

    private val timeWindow = Duration.of(properties.timeWindowAmount, properties.timeWindowUnit)
    private val eventTimestamps = mutableSetOf<LocalDateTime>()

    /**
     * Подсчитать событие
     *
     * @return true если рубильник активирован D:
     */
    fun countEventAndCheck(): Boolean {
        if (properties.maxEventsInTimeWindow <= 0) return false
        val nowTimestamp = LocalDateTime.now()
        eventTimestamps += nowTimestamp
        eventTimestamps.removeIf { it < nowTimestamp - timeWindow }
        return eventTimestamps.size > properties.maxEventsInTimeWindow
    }
}
