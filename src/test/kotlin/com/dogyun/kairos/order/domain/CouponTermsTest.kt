package com.dogyun.kairos.order.domain

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CouponTermsTest {
    private fun terms(min: Long?) = CouponTerms(
        1, CouponKind.PRODUCT, CouponIssuer.PLATFORM, CouponTarget.ALL_PRODUCTS, null, null,
        CouponDiscountType.FIXED, 1000, null, min,
    )

    @Test
    fun 최소_주문_금액과_같으면_충족한다() = assertTrue(terms(30000).meetsMinimum(30000))

    @Test
    fun `1원_모자라면_충족하지_않는다`() = assertFalse(terms(30000).meetsMinimum(29999))

    @Test
    fun 최소_주문_금액이_없으면_항상_충족한다() = assertTrue(terms(null).meetsMinimum(0))
}
