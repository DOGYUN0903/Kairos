package com.dogyun.kairos.order.domain

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/** `"K" + yyyyMMddHHmmss + [A-Z0-9]{6}`(21자). 토스 결제창의 orderId로도 쓴다. */
object OrderNo {
    private const val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val SUFFIX_LENGTH = 6
    private val FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    fun generate(now: LocalDateTime, random: Random): String {
        val suffix = buildString { repeat(SUFFIX_LENGTH) { append(CHARS[random.nextInt(CHARS.length)]) } }
        return "K" + FORMAT.format(now) + suffix
    }
}
