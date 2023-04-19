package org.huckster.exchange.bybit.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class PublicWebsocketMessage(

    @JsonProperty("req_id")
    val requestId: String? = null,

    val topic: String? = null
)
