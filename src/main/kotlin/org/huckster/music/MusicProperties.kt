package org.huckster.music

/**
 * Параметры проигрывания музыки
 */
data class MusicProperties(
    val playMusic: Boolean = false,
    val trackName: String? = null,
    val trackFile: String? = null,
)
