package org.huckster.exchange

import kotlinx.coroutines.flow.Flow
import org.huckster.orderbook.OrderbookDiff

/**
 * Интерфейс для бирж
 */
interface Exchange {

    /**
     * Получить процент комиссии на бирже
     */
    val feePercentage: Double

    /**
     * Получить все доступные символы для торговли на споте
     */
    suspend fun getAvailableSpotSymbols(): Set<String>

    /**
     * Подписаться на изменения стаканов
     *
     * @param symbols Символы
     * @return пары Символ - Изменение стакана
     */
    suspend fun listenToOrderbookDiff(symbols: Set<String>): Flow<Pair<String, OrderbookDiff>>
}
