package org.huckster

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
import org.huckster.notification.Notificator
import org.huckster.orderbook.OrderbookKeeper
import org.huckster.scam.ScamMaster
import org.huckster.storage.Storage
import java.time.Duration
import java.time.LocalDateTime
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

    // используется для ПРОТИВОСРАЧ™ kill-switch
    private val lastHourArbitrages = mutableSetOf<Arbitrage>()

    private val log = KotlinLogging.logger { }

    /**
     * Стартуем!
     */
    suspend fun run() = coroutineScope {
        val symbols = generateSymbols()

        // создаём нужные стаканы
        orderbookKeeper.createOrderbooks(symbols)

        // запуск обновления стаканов символов (пусть фоном обновляет)
        val orderbookUpdatingJob = launch(Dispatchers.Default) {
            while (isActive) {
                startOrderbookUpdate(symbols)

                if (properties.restartOrderbookUpdateInMillis < 0) {
                    log.warn(
                        "'restartOrderbookUpdateInMillis' property is negative, " +
                                "cancelling orderbook update job"
                    )
                    cancel()
                }

                // до сюда доходим если обновление стаканов рухнуло и надо рестартовать
                orderbookKeeper.clearAllOrderbooks()
                delay(properties.restartOrderbookUpdateInMillis.absoluteValue)
            }
        }

        // поиск арбитража
        launch(Dispatchers.Default) {
            while (orderbookUpdatingJob.isActive) {
                findAndExecuteArbitrages(orderbookUpdatingJob)
                delay(10)
            }
        }
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
        val exchange = exchange.getAvailableSpotSymbols()

        // ищем пересечение между сгенерированными символами и теми, что есть на бирже
        val symbolsIntersect = generatedSymbols.intersect(exchange)

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

        if (symbolsIntersect.isEmpty()) {
            throw IllegalStateException("No symbols to proceed")
        }

        return symbolsIntersect
    }

    // процесс обновления стаканов
    // может упасть к чертям в любой момент!
    private suspend fun startOrderbookUpdate(symbols: Set<String>) {
        try {
            exchange
                .listenToOrderbookDiff(symbols)
                .collect { (symbol, diff) -> orderbookKeeper.updateOrderbook(symbol, diff) }
        } catch (e: CancellationException) {
            log.warn("Orderbook update cancelled: ${e.message}")
            sendOrderbookUpdateCancelledNotification(e)
        } catch (e: RuntimeException) {
            log.error("Orderbook update failure: ${e.javaClass.simpleName}: ${e.message}", e)
            if (properties.notifyOrderbookUpdateFailure) {
                sendOrderbookUpdateFailureNotification(e)
            }
        }
    }

    // процесс поиска и исполнения арбитража
    private suspend fun findAndExecuteArbitrages(orderbookUpdatingJob: Job) {
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

                    antispamKillSwitch(bestArbitrage, orderbookUpdatingJob)

                    // временно тормозим чтоб не спамить
                    delay(properties.waitAfterArbitrageExecutionMillis)
                }
            }
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

    // отправить оповещение о поломке обновления стаканов
    private suspend fun sendOrderbookUpdateFailureNotification(exception: RuntimeException) = try {
        notificator.sendNotification {
            bold("\uD83E\uDEE3 Произошла ФАТАЛЬНАЯ ошибка в процессе обновления стаканов!\n\n")
            text("Код ошибки: ")
            code("${exception.javaClass.simpleName}: ${exception.message}")
        }
    } catch (e: RuntimeException) {
        log.error(
            "Cannot send notification about orderbook update error: " +
                    "${e.javaClass.simpleName} - ${e.message}"
        )
    }

    // отправить оповещение об отмене обновления стаканов
    private fun sendOrderbookUpdateCancelledNotification(exception: CancellationException) = runBlocking {
        try {
            notificator.sendNotification {
                bold("Отмена обновления стаканов. Huckster вырубается \uD83E\uDEE1\n\n")
                text("Код отмены: ")
                code(exception.message ?: "неизвестно")
            }
        } catch (e: RuntimeException) {
            log.error(
                "Cannot send notification about orderbook update error: " +
                        "${e.javaClass.simpleName} - ${e.message}"
            )
        }
    }

    // отправить оповещение о найденном арбитраже
    private suspend fun sendArbitrageFoundNotification(arbitrage: Arbitrage) = try {
        notificator.sendNotification {
            bold("\uD83D\uDCB5 Найден арбитраж: профит ${arbitrage.profitPercentage()}%\n\n")
            arbitrage.orders.forEachIndexed { i, order ->
                text("${i + 1}) ${order.type.description} ${order.symbol} за ${order.price}\n")
            }
            text("\n")
            code("${arbitrage.id}")
        }

    } catch (e: RuntimeException) {
        log.error("Notification sending failed! ${e.javaClass.simpleName} - ${e.message}")
    }

    // ПРОТИВОСРАЧ™ kill-switch
    // вырубаем huckster если зафиксировали беспорядочный спам арбитражей (обычно случается когда что-то грохнулось)
    private fun antispamKillSwitch(arbitrage: Arbitrage, orderbookUpdatingJob: Job) {
        lastHourArbitrages += arbitrage
        lastHourArbitrages.removeIf { it.timestamp < LocalDateTime.now() - Duration.ofHours(1) }

        val maxArbitragesPerHour = (60 * 60 * 1000 / properties.waitAfterArbitrageExecutionMillis).toInt()

        if (lastHourArbitrages.size > maxArbitragesPerHour / 4) {
            orderbookUpdatingJob.cancel("ПРОТИВОСРАЧ™ kill switch activated")
        }
    }

    private fun Iterable<Any>.forEachItem(block: (String) -> Unit) =
        forEachIndexed { index, item ->
            block("#${index + 1}\t$item")
        }
}
