package org.huckster

import org.huckster.arbitrator.ArbitratorProperties
import org.huckster.exchange.ExchangeProperties
import org.huckster.music.MusicProperties
import org.huckster.notification.NotificationProperties
import org.huckster.scam.ScamProperties
import org.huckster.storage.StorageProperties

/**
 * Параметры (корень)
 */
data class ApplicationProperties(

    /**
     * Игнорировать символы, отсутствующие на бирже
     *
     * Если false, то программа не запустится даже если на бирже не будет какого-то одного символа!
     */
    val ignoreUnsupportedSymbols: Boolean = false,

    /**
     * Оповещать о старте обновления стаканов
     */
    val notifyOrderbookUpdateStart: Boolean = false,

    /**
     * Оповещать о возникновении фатальной ошибки при обновлении стаканов
     */
    val notifyOrderbookUpdateFailure: Boolean = false,

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
     * Параметры уведомлений
     */
    val notificator: NotificationProperties,

    /**
     * Параметры хранилища
     */
    val storage: StorageProperties
)
