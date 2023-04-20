package org.huckster.exchange

import kotlinx.coroutines.flow.Flow
import org.huckster.orderbook.OrderbookDiff

/**
 * Интерфейс для бирж
 */
interface Exchange {

    /**
     * Получить все доступные символы для торговли на споте
     */
    suspend fun getAvailableSpotSymbols(): Set<String>

    suspend fun listenToOrderbookDiff(symbols: Set<String>): Flow<Pair<String, OrderbookDiff>>
}
