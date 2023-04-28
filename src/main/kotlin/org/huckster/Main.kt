package org.huckster

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.huckster.arbitrator.Arbitrator
import org.huckster.exchange.Exchange
import org.huckster.exchange.binance.BinanceExchange
import org.huckster.music.MusicPlayer
import org.huckster.orderbook.Orderbook
import org.huckster.scam.ScamMaster
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

private val log = KotlinLogging.logger("Main")

fun main(args: Array<String>): Unit = runBlocking {

    // здравствуйте представьтесь
    printBanner()
    log.info("Welcome to Huckster! Good luck...")

    // профиль
    val profile = args.firstOrNull()
    if (profile != null) {
        log.info("Selected profile: $profile")
    } else {
        log.info("No profile selected")
    }

    // а ну ка конфиги загрузи
    val properties = loadProperties(profile)
    if (properties == null) {
        log.error("Wow! I cannot work without some properties...")
        return@runBlocking
    }
    if (log.isDebugEnabled) {
        with(properties.arbitrator) {
            log.debug("Stable coins (${stableCoins.size}):")
            stableCoins.forEach { coin -> log.debug(" - $coin") }

            log.debug("Major coins (${majorCoins.size}):")
            majorCoins.forEach { coin -> log.debug(" - $coin") }

            log.debug("Shit coins (${shitCoins.size}):")
            shitCoins.forEach { coin -> log.debug(" - $coin") }
        }
    }

    // саша врубай!
    if (properties.music != null) {
        thread(name = "MusicThread") {
            val musicPlayer = MusicPlayer(properties.music)
            musicPlayer.play()
        }
    }

    val arbitrator = Arbitrator(properties.arbitrator)
    val exchange = BinanceExchange(properties.exchange.binance)
    val scamMaster = ScamMaster(properties.scam)

    // определение символов
    val symbols = generateSymbols(arbitrator, exchange, properties.ignoreUnsupportedSymbols)
    if (symbols.isEmpty()) {
        log.error("Damn! I need some symbols to work...")
        return@runBlocking
    }

    // стаканы по символам активов
    val orderbooks = ConcurrentHashMap<String, Orderbook>()
    symbols.forEach { orderbooks[it] = Orderbook(10) }

    // запуск обновления стаканов символов (пусть фоном обновляет)
    val orderbookUpdatingJob = launch(Dispatchers.Default) {
        exchange
            .listenToOrderbookDiff(symbols)
            .collect { (symbol, diff) ->
                orderbooks[symbol]?.update(diff.newAsks, diff.newBids)
            }
    }

    // поиск и исполнение арбитража (пока только поиск)
    launch(Dispatchers.Default) {
        while (orderbookUpdatingJob.isActive) {

            // а есть чем поживиться?
            val arbitrages = arbitrator.findArbitrage(orderbooks).sortedByDescending { it.profit }

            // если есть...
            if (arbitrages.isNotEmpty()) {

                // берём лучшую сделку
                val bestArbitrage = arbitrages.first()

                // нужен аппрув
                if (scamMaster.isApproved(bestArbitrage)) {
                    log.warn("ARBITRAGE APPROVED BY SCAM MASTER D:")
                    log.info("Profit ${(bestArbitrage.profit - 1) * 100}%")
                    bestArbitrage.orders.forEach { log.info("- ${it.type} ${it.symbol} for ${it.price}") }
                }
            }

            delay(50)
        }
    }
}

// напечатай БАНнер
private fun printBanner() {
    val banner = readResourceAsString("banner.txt")
    println("\u001B[32m$banner\u001B[0m")
}

// загрузи параметры
private fun loadProperties(profile: String?): ApplicationProperties? {
    val objectMapper = ObjectMapper(YAMLFactory()).configureJacksonMapper()

    val fileName = if (profile != null) "config-$profile.yaml" else "config.yaml"
    log.info("Using properties from file: $fileName")

    val propertiesString = readResourceAsString(fileName)

    if (propertiesString.isNullOrEmpty()) {
        log.error("Properties file not found or it is empty: $fileName")
        return null
    }

    return objectMapper.readValue(propertiesString)
}

// из resources дай файлик
private fun readResourceAsString(path: String): String? {
    return object {}.javaClass.classLoader.getResource(path)?.readText()
}

// сгенерируй символы для обновления стаканов
private suspend fun generateSymbols(
    arbitrator: Arbitrator,
    exchange: Exchange,
    ignoreUnsupportedSymbols: Boolean
): Set<String> {
    val generatedSymbols = arbitrator.generateSymbols()

    log.info("Generated ${generatedSymbols.size} symbols:")
    generatedSymbols.forEachIndexed { index, symbol -> log.info("#${index + 1}\t$symbol") }

    log.info("Checking exchange for generated symbols...")
    val binanceSymbols = exchange.getAvailableSpotSymbols()

    val symbols = generatedSymbols.intersect(binanceSymbols)

    if (symbols.size < generatedSymbols.size) {
        log.warn("Some symbols are not supported:")

        generatedSymbols
            .minus(symbols)
            .forEachIndexed { index, symbol -> log.warn("#${index + 1}\t$symbol") }

        if (!ignoreUnsupportedSymbols) return setOf()
    } else {
        log.info("All symbols are good!")
    }

    return symbols
}
