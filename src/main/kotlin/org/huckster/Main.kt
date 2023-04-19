package org.huckster

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.huckster.arbitrator.Arbitrator
import org.huckster.exchange.bybit.BybitExchange
import org.huckster.orderbook.Orderbook
import org.huckster.properties.ApplicationProperties

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

    val arbitrator = Arbitrator(properties.arbitrator)

    val symbols = arbitrator.generateSymbols()

    log.info("Generated: ----------------------")
    symbols.forEachIndexed { index, symbol -> log.info("$index\t$symbol") }

    val bybit = BybitExchange(properties.exchange.bybit)
//    bybit.init()

    val bybitSpotSymbols = bybit.getAvailableSpotSymbols()

    println("From bybit: -----------------------")
    bybitSpotSymbols.forEach { println(it) }

    println("Intersect: ------------------------")
    val intersect = symbols.intersect(bybitSpotSymbols)
    intersect.forEach { println(it) }

    println("Missing ${symbols.size - intersect.size}")

//
//    launch(Dispatchers.Default) { bybit.listen() }
//
//    val orderbooks = mutableMapOf<String, Orderbook>()
//
//    orderbooks["BTCUSDT"] = Orderbook(1)
//
//    bybit.subscribeToOrderbook(setOf("BTCUSDT" to 1)) { message ->
//        val bids = message.data.bids.associate { array -> array[0].toDouble() to array[1].toDouble() }
//        val asks = message.data.asks.associate { array -> array[0].toDouble() to array[1].toDouble() }
//
//        val orderbook = orderbooks[message.data.symbol]
//
//        if (orderbook != null) {
//            if (message.type == "snapshot") {
//                orderbook.clear()
//            }
//
//            orderbook.update(bids, asks)
//
//            printOrderbook(orderbook)
//        }
//    }
//
//    delay(3_000) // не delay говорю!
//
//    val apiKeyInfo = bybit.getApiKeyInfo().result
//
//    println("API Key Information:")
//    println("ID: ${apiKeyInfo?.id}")
//    println("Name: ${apiKeyInfo?.note}")
//    println("Key: ${apiKeyInfo?.apiKey}")
//    println("Created: ${apiKeyInfo?.createdAt}")
//    println("Expiring: ${apiKeyInfo?.expiredAt}")
}

private fun printOrderbook(orderbook: Orderbook): Int {
    val sb = StringBuilder("Orderbook: \n")
    orderbook.asks.forEach { (price, value) ->
        sb.append("ASK $price - $value\n")
    }
    sb.append("----------------------\n")
    orderbook.bids.forEach { (price, value) ->
        sb.append("BID $price - $value\n")
    }
    print(sb.toString())
    return sb.length
}

private fun printBanner() {
    val banner = readResourceAsString("banner.txt")
    println("\u001B[32m$banner\u001B[0m")
}

private fun loadProperties(profile: String?): ApplicationProperties? {
    val objectMapper = ObjectMapper(YAMLFactory()).configureJacksonMapper()

    val fileName = if (profile != null) "config-$profile.yaml" else "config.yaml"
    log.debug("Using properties from file: $fileName")

    val propertiesString = readResourceAsString(fileName)

    if (propertiesString.isNullOrEmpty()) {
        log.error("Properties file not found or it is empty: $fileName")
        return null
    }

    return objectMapper.readValue(propertiesString)
}

private fun readResourceAsString(path: String): String? {
    return object {}.javaClass.classLoader.getResource(path)?.readText()
}
