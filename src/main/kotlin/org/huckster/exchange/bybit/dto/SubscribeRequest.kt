package org.huckster.exchange.bybit.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class SubscribeRequest(

    @JsonProperty("req_id")
    val requestId: String? = null,

    val op: String = "subscribe",

    val args: List<String>
)
