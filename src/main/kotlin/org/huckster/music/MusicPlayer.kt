package org.huckster.music

import mu.KotlinLogging
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine

class MusicPlayer(private val properties: MusicProperties) {

    private val log = KotlinLogging.logger { }

    fun play() {
        if (!properties.playMusic || properties.trackFile == null) return

        val musicName = properties.trackName ?: properties.trackFile
        log.info("\u001B[33m♪ Now playing: ${musicName}, enjoy!!!\u001B[0m")

        var dataLine: SourceDataLine? = null
        try {
            val inputStream = object {}.javaClass.classLoader.getResourceAsStream(properties.trackFile)
            val audioStream = AudioSystem.getAudioInputStream(inputStream)
            val format = audioStream.format

            val info = DataLine.Info(SourceDataLine::class.java, audioStream.format)

            dataLine = AudioSystem.getLine(info) as SourceDataLine

            dataLine.open(format, 524288)
            dataLine.start()

            val volumeControl = dataLine.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            volumeControl.value = -10.0f

            var readBytes = 0
            val buffer = ByteArray(524288)

            while (readBytes != -1) {
                readBytes = audioStream.read(buffer, 0, buffer.size)
                if (readBytes >= 0) {
                    dataLine.write(buffer, 0, readBytes)
                }
            }
        } catch (e: RuntimeException) {
            log.error("Cannot play sound: ${properties.trackName}: ${e.message}")
        } finally {
            dataLine?.drain()
            dataLine?.close()
        }
    }
}
