package org.huckster.application

import org.huckster.arbitrage.ArbitratorProperties
import org.huckster.exchange.ExchangeProperties
import org.huckster.health.EventFrequencyKillSwitchProperties
import org.huckster.music.MusicProperties
import org.huckster.notification.NotificationProperties
import org.huckster.orderbook.OrderbookProperties
import org.huckster.scam.ScamProperties
import org.huckster.storage.StorageProperties

/**
 * Параметры Huckster
 */
data class ApplicationProperties(

    /**
     * Игнорировать символы, отсутствующие на бирже
     *
     * Если false, то программа не запустится даже если на бирже не будет какого-то одного символа!
     */
    val ignoreUnsupportedSymbols: Boolean = false,

    /**
     * Оповещать о возникновении фатальной ошибки при обновлении стаканов
     */
    val notifyOrderbookUpdateFailure: Boolean = false,

    /**
     * Через сколько миллисекунд перезапускать обновление стаканов в случае падения.
     *
     * Чтобы не перезапускать напиши -1
     */
    val restartDelayMillis: Long = -1,

    /**
     * Параметры рубильника для частоты перезапуска
     */
    val tooManyRestartsKillSwitch: EventFrequencyKillSwitchProperties = EventFrequencyKillSwitchProperties(),

    /**
     * Сколько миллисекунд ждать после выполнения арбитража.
     *
     * Нужно чтоб не спамить
     */
    val arbitrageExecutionDelayMillis: Long = 5000,

    /**
     * Параметры рубильника для частоты исполнения арбитражей
     */
    val tooManyArbitragesKillSwitch: EventFrequencyKillSwitchProperties = EventFrequencyKillSwitchProperties(),

    /**
     * Параметры музыки (режим БТБТ)
     */
    val music: MusicProperties? = null,

    /**
     * Параметры стаканов
     */
    val orderbook: OrderbookProperties = OrderbookProperties(),

    /**
     * Параметры биржи
     */
    val exchange: ExchangeProperties,

    /**
     * Параметры арбитратора
     */
    val arbitrator: ArbitratorProperties = ArbitratorProperties(),

    /**
     * Параметры скам мастера
     */
    val scam: ScamProperties = ScamProperties(),

    /**
     * Параметры уведомлений
     */
    val notificator: NotificationProperties,

    /**
     * Параметры хранилища
     */
    val storage: StorageProperties
)
