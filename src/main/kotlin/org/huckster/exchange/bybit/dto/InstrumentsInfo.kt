package org.huckster.exchange.bybit.dto

data class InstrumentsInfo(

    val retCode: Int?,

    val retMsg: String?,

    val result: Result?
) {

    data class Result(
        val list: List<Instrument>
    )

    data class Instrument(
        val symbol: String
    )
}
