package org.huckster

import org.huckster.arbitrator.ArbitratorProperties
import org.huckster.exchange.ExchangeProperties
import org.huckster.music.MusicProperties
import org.huckster.scam.ScamProperties

/**
 * Параметры (корень)
 */
data class ApplicationProperties(

    /**
     * Параметры музыки (режим БТБТ)
     */
    val music: MusicProperties?,

    /**
     * Параметры биржи
     */
    val exchange: ExchangeProperties,

    /**
     * Параметры арбитратора
     */
    val arbitrator: ArbitratorProperties,

    /**
     * Параметры скам мастера
     */
    val scam: ScamProperties,

    /**
     * Игнорировать символы, отсутствующие на бирже
     *
     * Если false, то программа не запустится даже если на бирже не будет какого-то одного символа!
     */
    val ignoreUnsupportedSymbols: Boolean = false,
)
