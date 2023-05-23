package org.huckster.registrar

data class ArbitrageRegistrarProperties(
    val sendNotifications: Boolean = true,
    val useStorage: Boolean = true,
)
