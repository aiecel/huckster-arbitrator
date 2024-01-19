package org.huckster

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.huckster.application.Application
import org.huckster.application.ApplicationProperties
import org.huckster.music.MusicPlayer
import org.huckster.music.MusicProperties
import org.huckster.util.configureJacksonMapper
import java.io.FileReader
import java.io.IOException

private val log = KotlinLogging.logger("Main")

fun main(args: Array<String>): Unit = runBlocking {

    // здравствуйте представьтесь
    printBanner()
    log.info("Welcome to Huckster! Good luck...")

    // определи профиль
    val profile = getProfile(args)

    // а ну ка конфиги загрузи
    val properties = loadProperties(profile)

    // саша врубай!
    playMusicIfNeeded(properties.music)

    val application = Application(properties)
    application.run()

    log.info("Goodbye!")
}

// напечатай БАНнер
private fun printBanner() {
    val banner = try {
        object {}.javaClass.classLoader.getResource("banner.txt")?.readText()
    } catch (e: IOException) {
        log.error("Cannot load banner: ${e.message}")
        "Huckster Arbitrator"
    }
    println("\u001B[32m$banner\u001B[0m")
}

// определи профиль
private fun getProfile(args: Array<String>): String? {
    log.debug("Program arguments: ${args.contentDeepToString()}")

    val profile = args.firstOrNull()
    if (profile != null) {
        log.info("Selected profile: $profile")
    } else {
        log.info("No profile selected")
    }

    return profile
}

// загрузи параметры
private fun loadProperties(profile: String?): ApplicationProperties {
    val objectMapper = ObjectMapper(YAMLFactory()).configureJacksonMapper()

    val filePath = if (profile != null) "configs/$profile.yaml" else "configs/config.yaml"
    log.info("Using properties from file: $filePath")

    val propertiesString = try {
        FileReader(filePath).readText()
    } catch (e: IOException) {
        throw IOException("Cannot load properties: ${e.message}")
    }

    return try {
        objectMapper.readValue(propertiesString)
    } catch (e: JacksonException) {
        throw IllegalArgumentException(
            "Cannot parse properties from file $filePath: " +
                    "error at ${e.location} - ${e.originalMessage}"
        )
    }
}

// серёжа погнали
private fun playMusicIfNeeded(properties: MusicProperties?) {
    if (properties == null || !properties.playMusic) {
        return
    }

    if (properties.trackFile.isNullOrBlank()) {
        log.warn("Specify 'music.trackFile' to play music!")
        return
    }

    try {
        val musicPlayer = MusicPlayer()
        musicPlayer.play(properties.trackName, properties.trackFile)
    } catch (e: RuntimeException) {
        log.warn("Error playing track ${properties.trackFile}: ${e.message}")
    }
}
