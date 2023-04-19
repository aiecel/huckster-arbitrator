package org.huckster.exchange

/**
 * Интерфейс для бирж
 */
interface Exchange {

    /**
     * Получить все доступные символы для торговли на споте
     */
    suspend fun getAvailableSpotSymbols(): Set<String>
}
