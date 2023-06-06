package org.huckster.orderbook

import java.time.Instant
import java.util.*

/**
 * Стакан
 *
 * [Методичка](https://en.wikipedia.org/wiki/Order_book)
 */
class Orderbook(

    /**
     * Максимальное число ASKов и BIDов, которое может хранить стакан
     */
    private val size: Int = 10
) {

    /**
     * ASKи - записи о продавцах (красные)
     */
    val asks: SortedMap<Double, Double> = TreeMap(compareByDescending { it })

    /**
     * BIDы - записи о покупателях (зелёные)
     */
    val bids: SortedMap<Double, Double> = TreeMap(compareByDescending { it })

    /**
     * Время последнего обновления стакана
     */
    var lastUpdatedTimestamp: Instant = Instant.now(); private set

    /**
     * Обновить стакан
     *
     * Если объём равен нулю, то запись удаляется.
     *
     * После обновления стакана он обрезается -
     * остаются [size] самых дешёвых продавцов, [size] самых дорогих покупателей
     *
     * //todo починить описание
     */
    fun update(orderbookDiff: OrderbookDiff) {
        updateAsksAndBids(orderbookDiff.newAsks, orderbookDiff.newBids)
        lastUpdatedTimestamp = orderbookDiff.timestamp
    }

    /**
     * Обновить стакан
     *
     * Если объём равен нулю, то запись удаляется.
     *
     * После обновления стакана он обрезается -
     * остаются [size] самых дешёвых продавцов, [size] самых дорогих покупателей
     *
     * @param newAsks новые ASK (цена -> объём)
     * @param newBids новые BID (цена -> объём)
     */
    fun update(newAsks: Map<Double, Double> = mapOf(), newBids: Map<Double, Double> = mapOf()) {
        updateAsksAndBids(newAsks, newBids)
        lastUpdatedTimestamp = Instant.now()
    }

    /**
     * Очистить стакан
     */
    fun clear() {
        asks.clear()
        bids.clear()
    }

    private fun updateAsksAndBids(newAsks: Map<Double, Double>, newBids: Map<Double, Double>) {
        if (newAsks.isNotEmpty()) {
            newAsks.forEach { (price, volume) ->
                if (volume == 0.0) asks -= price else asks[price] = volume
            }
            trimStart(asks, size)
        }
        if (newBids.isNotEmpty()) {
            newBids.forEach { (price, volume) ->
                if (volume == 0.0) bids -= price else bids[price] = volume
            }
            trimEnd(bids, size)
        }
    }

    private fun trimStart(map: SortedMap<Double, Double>, size: Int) {
        if (map.size <= size) return
        val toRemove = map.size - size
        val keysToRemove = map.keys.toList().subList(0, toRemove)
        map.keys.removeAll(keysToRemove.toSet())
    }

    private fun trimEnd(map: SortedMap<Double, Double>, size: Int) {
        if (map.size <= size) return
        val toRemove = map.size - size
        val keysList = map.keys.toList()
        val keysToRemove = keysList.subList(keysList.size - toRemove, keysList.size)
        map.keys.removeAll(keysToRemove.toSet())
    }
}
