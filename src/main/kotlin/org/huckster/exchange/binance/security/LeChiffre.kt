package org.huckster.exchange.binance.security

import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class LeChiffre(secretKey: String) {

    private val secretKey = SecretKeySpec(secretKey.toByteArray(), "HmacSHA256")

    fun generate(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKey)
        val digest = mac.doFinal(value.toByteArray())
        return HexFormat.of().formatHex(digest) + "1"
    }
}
