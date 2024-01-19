package org.huckster.application

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.huckster.arbitrage.Arbitrator
import org.huckster.arbitrage.model.Arbitrage
import org.huckster.exchange.binance.BinanceExchange
import org.huckster.health.EventFrequencyKillSwitch
import org.huckster.notification.NotificationException
import org.huckster.notification.Notificator
import org.huckster.notification.applicationShutdown
import org.huckster.notification.arbitrageFound
import org.huckster.notification.orderbookUpdateFailure
import org.huckster.orderbook.OrderbookKeeper
import org.huckster.scam.ScamMaster
import org.huckster.storage.Storage
import sun.security.krb5.internal.KDCOptions.with
import java.util.concurrent.CancellationException
import kotlin.math.absoluteValue

/**
 * Так называемое приложение Huckster
 */
class Application(private val properties: ApplicationProperties) {

    private val orderbookKeeper = OrderbookKeeper(properties.orderbook)
    private val exchange = BinanceExchange(properties.exchange.binance)
    private val arbitrator = Arbitrator(properties.arbitrator, orderbookKeeper, exchange)
    private val scamMaster = ScamMaster(properties.scam, orderbookKeeper)
    private val notificator = Notificator(properties.notificator)
    private val storage = Storage(properties.storage)

    // рубильник на частоту рестартов
    private val tooManyRestartsKillSwitch = EventFrequencyKillSwitch(properties.tooManyRestartsKillSwitch)

    // рубильник на частоту выполнения арбитражей
    private val tooManyArbitragesExecutedKillSwitch = EventFrequencyKillSwitch(properties.tooManyArbitragesKillSwitch)

    private val log = KotlinLogging.logger { }

    private var orderbookUpdatingJob: Job? = null

    /**
     * Стартуем!
     */
    suspend fun run() = coroutineScope {
        val symbols = generateSymbols()

        if (symbols.isEmpty()) {
            log.info("No symbols to proceed")
            return@coroutineScope
        }

        // создаём нужные стаканы
        orderbookKeeper.createOrderbooks(symbols)

        // запуск обновления стаканов символов (пусть фоном обновляет)
        orderbookUpdatingJob = launch(Dispatchers.Default) {
            while (isActive) {
                updateOrderbooks(symbols)

                // если рестарт выключен вырубаемся
                if (properties.restartDelayMillis < 0) {
                    log.warn("'restartOrderbookUpdateInMillis' property is negative, restart is disabled")
                    shutdown("Restart disabled")
                }

                // вырубаемся если зафиксировали спам рестартов
                if (tooManyRestartsKillSwitch.countEventAndCheck()) {
                    shutdown("ПРОТИВОСРАЧ™ kill switch activated (restarts)")
                }

                // до сюда доходим если надо рестартовать
                log.info("Will be restarting orderbook update in ${properties.restartDelayMillis} ms")
                orderbookKeeper.clearAllOrderbooks()
                delay(properties.restartDelayMillis.absoluteValue)
            }
        }

        // поиск арбитража
        launch(Dispatchers.Default) {
            while (isActive) {
                findAndExecuteArbitrages()
                delay(10)
            }
        }
    }

    /**
     * Стоп машина!
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun shutdown(message: String = "Shutdown initiated") = runBlocking {
        log.info("Shutting down: $message")
        orderbookUpdatingJob?.cancel(message)
        orderbookUpdatingJob = null
        orderbookKeeper.clearAllOrderbooks()
        sendShutdownNotification(message)
    }

    // генерация символов для работы
    // берёт список символов от арбитратора и сверяет их с биржей
    private suspend fun generateSymbols(): Set<String> {
        if (log.isDebugEnabled) {
            with(properties.arbitrator) {
                log.debug("Stable coins (${stableCoins.size}):")
                stableCoins.forEachItem { log.debug(it) }

                log.debug("Major coins (${majorCoins.size}):")
                majorCoins.forEachItem { log.debug(it) }

                log.debug("Shit coins (${shitCoins.size}):")
                shitCoins.forEachItem { log.debug(it) }
            }
        }

        log.info("Generating symbols")
        val generatedSymbols = arbitrator.generateSymbols()

        log.info("Arbitrator generated ${generatedSymbols.size} symbols:")
        generatedSymbols.forEachItem { log.info(it) }

        log.info("Checking exchange for generated symbols...")
        val exchangeSymbols = exchange.getAvailableSpotSymbols()

        // ищем пересечение между сгенерированными символами и теми, что есть на бирже
        val symbolsIntersect = generatedSymbols.intersect(exchangeSymbols)

        if (symbolsIntersect.size < generatedSymbols.size) {
            val unsupportedSymbols = generatedSymbols.minus(symbolsIntersect)

            log.warn("Some symbols are not supported (${unsupportedSymbols.size}):")
            unsupportedSymbols.forEachItem { log.warn(it) }

            if (!properties.ignoreUnsupportedSymbols) {
                throw IllegalStateException(
                    "Startup failed: some generated symbols are not supported. " +
                            "Set 'ignoreUnsupportedSymbols=true' to ignore"
                )
            }
        } else {
            log.info("All symbols are good!")
        }

        return symbolsIntersect
    }

    // процесс обновления стаканов
    // может упасть к чертям в любой момент!
    private suspend fun updateOrderbooks(symbols: Set<String>) {
        try {
            exchange
                .listenToOrderbookDelta(symbols)
                .collect { (symbol, diff) -> orderbookKeeper.updateOrderbook(symbol, diff) }
        } catch (e: CancellationException) {
            log.info("Orderbook update cancelled: ${e.message}")
        } catch (e: RuntimeException) {
            log.error("Orderbook update failure: ${e.javaClass.simpleName}: ${e.message}", e)
            if (properties.notifyOrderbookUpdateFailure) {
                sendOrderbookUpdateFailureNotification(e)
            }
        }
    }

    // процесс поиска и исполнения арбитража
    private suspend fun findAndExecuteArbitrages() {
        try {
            // а есть чем поживиться?
            val arbitrages = arbitrator.findAllArbitrages().sortedByDescending { it.profit }

            // если есть...
            if (arbitrages.isNotEmpty()) {

                // берём лучшую сделку
                val bestArbitrage = arbitrages.first()

                log.info("Found ${arbitrages.size} arbitrage(s)")
                log.info("Best arbitrage: ${bestArbitrage.id}, profit ${bestArbitrage.profitPercentage()}%")
                bestArbitrage.orders.forEach { log.info("- ${it.type} ${it.symbol} for ${it.price}") }

                // нужен аппрув
                if (scamMaster.isApproved(bestArbitrage)) {
                    executeArbitrage(bestArbitrage)

                    // вырубаемся если зафиксировали спам арбитражами
                    if (tooManyArbitragesExecutedKillSwitch.countEventAndCheck()) {
                        shutdown("ПРОТИВОСРАЧ™ kill switch activated (arbitrages)")
                    }

                    // временно тормозим поиск арбитражей после исполнения
                    delay(properties.arbitrageExecutionDelayMillis)
                }
            }
        } catch (e: CancellationException) {
            log.info("Arbitrage finding and executing cancelled: ${e.message}")
        } catch (e: RuntimeException) {
            log.error("Arbitrage finding and executing failed: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    // исполнение арбитража
    private suspend fun executeArbitrage(arbitrage: Arbitrage) {
        log.info("Executing arbitrage ${arbitrage.id}")

        sendArbitrageFoundNotification(arbitrage)

        try {
            storage.save(arbitrage)
        } catch (e: RuntimeException) {
            log.error("Cannot save arbitrage! ${e.javaClass.simpleName} - ${e.message}")
        }
    }

    // отправить оповещение об отмене обновления стаканов
    private fun sendShutdownNotification(message: String? = null) = runBlocking {
        try {
            notificator.sendNotification(applicationShutdown(message))
        } catch (e: NotificationException) {
            log.error("Cannot send shutdown notification: ${e.message}")
        }
    }

    // отправить оповещение о поломке обновления стаканов
    private suspend fun sendOrderbookUpdateFailureNotification(exception: RuntimeException) {
        try {
            notificator.sendNotification(orderbookUpdateFailure(exception))
        } catch (e: NotificationException) {
            log.error("Cannot send orderbook update error notification: ${e.message}")
        }
    }

    // отправить оповещение о найденном арбитраже
    private suspend fun sendArbitrageFoundNotification(arbitrage: Arbitrage) {
        try {
            notificator.sendNotification(arbitrageFound(arbitrage))
        } catch (e: NotificationException) {
            log.error("Cannot send arbitrage found notification: ${e.message}")
        }
    }

    private fun Iterable<Any>.forEachItem(block: (String) -> Unit) =
        forEachIndexed { index, item ->
            block("#${index + 1}\t$item")
        }
}
