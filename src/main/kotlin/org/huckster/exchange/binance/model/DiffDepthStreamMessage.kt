package org.huckster.exchange.binance.model

import com.fasterxml.jackson.annotation.JsonProperty

data class DiffDepthStreamMessage(
    val stream: String,
    val data: Data
) {
    data class Data(
        @JsonProperty("E") val timestamp: Long,
        @JsonProperty("b") val bids: List<Array<String>>,
        @JsonProperty("a") val asks: List<Array<String>>
    )
}
