package com.dogyun.kairos.benefit.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CouponPolicyTest {

    private val from = LocalDateTime.of(2026, 12, 1, 0, 0)
    private val until = LocalDateTime.of(2026, 12, 31, 0, 0)

    private fun policy(
        issuerType: IssuerType = IssuerType.PLATFORM,
        sellerId: Long? = null,
        couponType: CouponType = CouponType.PRODUCT,
        targetType: TargetType = TargetType.ALL_PRODUCTS,
        targetProductId: Long? = null,
        discountType: DiscountType = DiscountType.FIXED,
        discountValue: Long = 2_000,
        maxDiscount: Long? = null,
        validityDays: Int? = null,
        validFrom: LocalDateTime = from,
        validUntil: LocalDateTime = until,
    ) = CouponPolicy.create(
        name = "쿠폰", issuerType = issuerType, sellerId = sellerId, couponType = couponType, targetType = targetType,
        targetProductId = targetProductId, discountType = discountType, discountValue = discountValue,
        maxDiscount = maxDiscount, minOrderAmount = null, totalQuantity = null, validityDays = validityDays,
        validFrom = validFrom, validUntil = validUntil,
    )

    private fun assertInvalid(block: () -> Unit) {
        assertEquals(ErrorCode.COUPON_POLICY_INVALID, assertFailsWith<DomainException> { block() }.errorCode)
    }

    @Test
    fun 정률인데_최대_할인이_없으면_COUPON_POLICY_INVALID() {
        assertInvalid { policy(discountType = DiscountType.RATE, discountValue = 1000, maxDiscount = null) }
        policy(discountType = DiscountType.RATE, discountValue = 1000, maxDiscount = 5_000)
    }

    @Test
    fun 기간이_역전되면_COUPON_POLICY_INVALID() {
        assertInvalid { policy(validFrom = until, validUntil = from) }
        assertInvalid { policy(validFrom = from, validUntil = from) }
    }

    @Test
    fun 판매자_정책은_PRODUCT_쿠폰에_SELLER_또는_PRODUCT_대상만_된다() {
        policy(issuerType = IssuerType.SELLER, sellerId = 1, targetType = TargetType.SELLER)
        policy(issuerType = IssuerType.SELLER, sellerId = 1, targetType = TargetType.PRODUCT, targetProductId = 9)
        assertInvalid { policy(issuerType = IssuerType.SELLER, sellerId = 1, targetType = TargetType.ALL_PRODUCTS) }
        assertInvalid { policy(issuerType = IssuerType.SELLER, sellerId = 1, couponType = CouponType.CART, targetType = TargetType.NONE) }
        assertInvalid { policy(issuerType = IssuerType.SELLER, sellerId = null, targetType = TargetType.SELLER) }
    }

    @Test
    fun 장바구니_쿠폰의_대상은_NONE이다() {
        policy(couponType = CouponType.CART, targetType = TargetType.NONE)
        assertInvalid { policy(couponType = CouponType.CART, targetType = TargetType.ALL_PRODUCTS) }
        assertInvalid { policy(couponType = CouponType.PRODUCT, targetType = TargetType.NONE) }
    }

    @Test
    fun 만료일은_valid_until과_발급일_plus_validity_days_중_빠른_쪽이다() {
        val p = policy(validityDays = 7)
        assertEquals(until, p.expiresAtFor(LocalDateTime.of(2026, 12, 28, 0, 0)))
        assertEquals(LocalDateTime.of(2026, 12, 8, 0, 0), p.expiresAtFor(LocalDateTime.of(2026, 12, 1, 0, 0)))
    }

    @Test
    fun 종료된_정책은_requireClaimable이_COUPON_POLICY_ENDED다() {
        val p = policy()
        p.end()
        assertEquals(ErrorCode.COUPON_POLICY_ENDED, assertFailsWith<DomainException> { p.requireClaimable(from.plusDays(1)) }.errorCode)
    }

    @Test
    fun 기간_밖이면_requireClaimable이_COUPON_POLICY_ENDED다() {
        val p = policy()
        assertEquals(ErrorCode.COUPON_POLICY_ENDED, assertFailsWith<DomainException> { p.requireClaimable(from.minusSeconds(1)) }.errorCode)
        assertEquals(ErrorCode.COUPON_POLICY_ENDED, assertFailsWith<DomainException> { p.requireClaimable(until) }.errorCode)
        p.requireClaimable(from)
    }

    @Test
    fun end는_두_번_불러도_ENDED다() {
        val p = policy()
        p.end()
        p.end()
        assertEquals(CouponPolicyStatus.ENDED, p.status)
    }

    @Test
    fun 쿠폰은_만료_시각이_되면_EXPIRED로_보인다() {
        val c = Coupon(null, 1, 1, CouponStatus.AVAILABLE, null, from, until, null)
        assertEquals("AVAILABLE", c.displayStatus(until.minusSeconds(1)))
        assertEquals("EXPIRED", c.displayStatus(until))
        val used = Coupon(null, 1, 1, CouponStatus.USED, 5, from, until, from)
        assertEquals("USED", used.displayStatus(until.plusDays(1)))
    }
}
