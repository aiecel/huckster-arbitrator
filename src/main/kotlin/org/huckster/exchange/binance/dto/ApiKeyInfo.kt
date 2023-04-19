package org.huckster.exchange.binance.dto

import java.time.LocalDateTime

data class ApiKeyInfo(

    val retCode: Int?,

    val retMsg: String?,

    val result: Result?
) {

    data class Result(
        val id: String,
        val note: String,
        val apiKey: String,
        val createdAt: LocalDateTime?,
        val expiredAt: LocalDateTime?
    )
}
