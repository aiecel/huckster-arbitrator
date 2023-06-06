package org.huckster.storage

/**
 * Настройки хранилища
 */
data class StorageProperties(
    val url: String,
    val user: String,
    val password: String,
)
