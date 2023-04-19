package org.huckster.exchange.bybit.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class OrderbookData(
    val type: String,
    val data: Data,
) {
    data class Data(
        @JsonProperty("s") val symbol: String,
        @JsonProperty("b") val bids: List<Array<String>>,
        @JsonProperty("a") val asks: List<Array<String>>
    )
}
