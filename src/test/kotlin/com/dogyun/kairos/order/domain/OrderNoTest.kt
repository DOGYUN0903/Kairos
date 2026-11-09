package com.dogyun.kairos.order.domain

import java.time.LocalDateTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrderNoTest {

    @Test
    fun 주문번호는_K_14자리_시각_6자리_코드다() {
        val no = OrderNo.generate(LocalDateTime.of(2026, 11, 10, 9, 5, 3), Random(1))

        assertEquals(21, no.length)
        assertTrue(Regex("^K20261110090503[A-Z0-9]{6}$").matches(no), no)
    }

    @Test
    fun 시드가_다르면_코드가_다르다() {
        val now = LocalDateTime.of(2026, 11, 10, 9, 5, 3)

        assertTrue(OrderNo.generate(now, Random(1)) != OrderNo.generate(now, Random(2)))
    }
}
