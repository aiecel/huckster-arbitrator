package org.huckster

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.huckster.arbitrator.Arbitrator
import org.huckster.arbitrator.model.Arbitrage
import org.huckster.exchange.binance.BinanceExchange
import org.huckster.notification.Notificator
import org.huckster.orderbook.OrderbookKeeper
import org.huckster.scam.ScamMaster
import org.huckster.storage.Storage

/**
 * Так называемое приложение Huckster
 */
class Application(private val properties: ApplicationProperties) {

    private val orderbookKeeper = OrderbookKeeper()
    private val exchange = BinanceExchange(properties.exchange.binance)
    private val arbitrator = Arbitrator(properties.arbitrator, orderbookKeeper, exchange)
    private val scamMaster = ScamMaster(properties.scam, orderbookKeeper)
    private val notificator = Notificator(properties.notificator)
    private val storage = Storage(properties.storage)

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
                delay(10_000)
            }
        }

        // поиск арбитража
        launch(Dispatchers.Default) {
            while (orderbookUpdatingJob.isActive) {
                findAndExecuteArbitrages()
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
        } catch (e: RuntimeException) {
            log.error("Orderbook update failure: ${e.javaClass.simpleName}: ${e.message}")
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

                log.info("Found ${arbitrages.size} arbitrages, best profit: ${bestArbitrage.profit}")

                // нужен аппрув
                if (scamMaster.isApproved(bestArbitrage)) {
                    executeArbitrage(bestArbitrage)

                    // временно тормозим чтоб не спамить
                    delay(3000)
                }
            }
        } catch (e: RuntimeException) {
            log.error("Arbitrage finding and executing failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    // исполнение арбитража
    private suspend fun executeArbitrage(arbitrage: Arbitrage) {
        log.info("Found arbitrage: profit ${arbitrage.profit}%")
        arbitrage.orders.forEach { log.info("- ${it.type} ${it.symbol} for ${it.price}") }

        sendArbitrageFoundNotification(arbitrage)

        try {
            storage.save(arbitrage)
        } catch (e: RuntimeException) {
            log.error("Cannot save arbitrage! ${e.javaClass.simpleName} - ${e.message}")
        }
    }

    // отправить оповещение о поломке обновления стаканов
    private suspend fun sendOrderbookUpdateFailureNotification(exception: RuntimeException) = try {
        notificator.sendNotification(
            "🫣 *Произошла ФАТАЛЬНАЯ ошибка в процессе обновления стаканов\\!*\n\n" +
                    "Код ошибки: `${exception.javaClass.simpleName}: ${exception.message}`"
        )
    } catch (e: RuntimeException) {
        log.error(
            "Cannot send notification about orderbook update error: " +
                    "${e.javaClass.simpleName} - ${e.message}"
        )
    }

    // отправить оповещение о найденном арбитраже
    private suspend fun sendArbitrageFoundNotification(arbitrage: Arbitrage) = try {
        val profitEscapedString = arbitrage.profitPercentage().toString().escape()

        val notificationText = StringBuilder(
            "\uD83D\uDCB5 *Найден арбитраж: профит $profitEscapedString%*\n"
        )

        arbitrage.orders.forEachIndexed { i, order ->
            val priceEscapedString = order.price
                .toString()
                .escape()

            notificationText
                .append("\n")
                .append("${i + 1}\\) ${order.type.description} ${order.symbol} за $priceEscapedString")
        }

        notificationText
            .append("\n\n")
            .append("`${arbitrage.id.toString().escape()}`")

        notificator.sendNotification(notificationText.toString())
    } catch (e: RuntimeException) {
        log.error("Notification sending failed! ${e.javaClass.simpleName} - ${e.message}")
    }

    private fun String.escape() = this
        .replace(".", "\\.")
        .replace("-", "\\-")

    private fun Iterable<Any>.forEachItem(block: (String) -> Unit) =
        forEachIndexed { index, item ->
            block("#${index + 1}\t$item")
        }
}
