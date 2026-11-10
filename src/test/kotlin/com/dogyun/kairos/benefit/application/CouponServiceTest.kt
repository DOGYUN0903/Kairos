package com.dogyun.kairos.benefit.application

import com.dogyun.kairos.benefit.domain.CouponStatus
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CouponServiceTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val clock = Clock.fixed(Instant.parse("2026-12-10T01:00:00Z"), zone)
    private val now = LocalDateTime.now(clock)
    private val policyRepository = FakeCouponPolicyRepository()
    private val couponRepository = FakeCouponRepository()
    private val productReader = FakeBenefitProductReader()
    private val policyService = CouponPolicyService(policyRepository, productReader, clock)
    private val couponService = CouponService(policyRepository, couponRepository, clock)

    private fun command(
        couponType: String = "PRODUCT",
        targetType: String = "ALL_PRODUCTS",
        targetProductId: Long? = null,
        totalQuantity: Int? = null,
        validityDays: Int? = null,
    ) = CreateCouponPolicyCommand(
        name = "쿠폰", couponType = couponType, targetType = targetType, targetProductId = targetProductId,
        discountType = "FIXED", discountValue = 2_000, maxDiscount = null, minOrderAmount = null,
        totalQuantity = totalQuantity, validityDays = validityDays,
        validFrom = now.minusDays(1), validUntil = now.plusDays(30),
    )

    private fun assertCode(code: ErrorCode, block: () -> Unit) {
        assertEquals(code, assertFailsWith<DomainException> { block() }.errorCode)
    }

    @Test
    fun 발급하면_AVAILABLE_쿠폰과_issued_count_1이다() {
        val policyId = policyService.createByAdmin(command(validityDays = 7))

        val claimed = couponService.claim(1L, policyId)

        val coupon = couponRepository.findById(claimed.couponId)!!
        assertEquals(CouponStatus.AVAILABLE, coupon.status)
        assertEquals(1L, coupon.memberId)
        assertEquals(now.plusDays(7), coupon.expiresAt)
        assertEquals(claimed.expiresAt, coupon.expiresAt)
        assertEquals(1, policyRepository.findById(policyId)!!.issuedCount)
    }

    @Test
    fun 총_수량이_소진되면_COUPON_SOLD_OUT() {
        val policyId = policyService.createByAdmin(command(totalQuantity = 1))
        couponService.claim(1L, policyId)

        assertCode(ErrorCode.COUPON_SOLD_OUT) { couponService.claim(2L, policyId) }
        assertEquals(1, policyRepository.findById(policyId)!!.issuedCount)
    }

    @Test
    fun 이미_받은_정책은_COUPON_ALREADY_ISSUED() {
        val policyId = policyService.createByAdmin(command(totalQuantity = 5))
        couponService.claim(1L, policyId)

        assertCode(ErrorCode.COUPON_ALREADY_ISSUED) { couponService.claim(1L, policyId) }
        assertEquals(1, policyRepository.findById(policyId)!!.issuedCount)
    }

    @Test
    fun 종료된_정책은_COUPON_POLICY_ENDED() {
        val policyId = policyService.createByAdmin(command())
        policyService.end(policyId)

        assertCode(ErrorCode.COUPON_POLICY_ENDED) { couponService.claim(1L, policyId) }
    }

    @Test
    fun 없는_정책은_COUPON_POLICY_NOT_FOUND() {
        assertCode(ErrorCode.COUPON_POLICY_NOT_FOUND) { couponService.claim(1L, 99L) }
    }

    @Test
    fun 판매자가_남의_상품을_대상으로_하면_COUPON_POLICY_INVALID() {
        productReader.put(10L, sellerId = 2L)

        assertCode(ErrorCode.COUPON_POLICY_INVALID) {
            policyService.createBySeller(1L, command(targetType = "PRODUCT", targetProductId = 10L))
        }
        assertCode(ErrorCode.COUPON_POLICY_INVALID) {
            policyService.createBySeller(1L, command(targetType = "PRODUCT", targetProductId = 404L))
        }
        assertEquals(0, policyRepository.policies.size)
    }

    @Test
    fun 판매자는_자기_상품과_자기_상점_대상_정책을_만들고_couponType은_PRODUCT로_강제된다() {
        productReader.put(10L, sellerId = 1L)

        val own = policyService.createBySeller(1L, command(couponType = "CART", targetType = "PRODUCT", targetProductId = 10L))
        val shop = policyService.createBySeller(1L, command(targetType = "SELLER"))

        assertEquals("PRODUCT", policyRepository.findById(own)!!.couponType.name)
        assertEquals(1L, policyRepository.findById(shop)!!.sellerId)
    }

    @Test
    fun 알_수_없는_열거값은_COUPON_POLICY_INVALID() {
        assertCode(ErrorCode.COUPON_POLICY_INVALID) { policyService.createByAdmin(command(targetType = "NOPE")) }
    }

    @Test
    fun 판매자는_남의_정책을_종료할_수_없다() {
        val policyId = policyService.createBySeller(1L, command(targetType = "SELLER"))

        assertCode(ErrorCode.COUPON_POLICY_NOT_FOUND) { policyService.endBySeller(2L, policyId) }
        policyService.endBySeller(1L, policyId)
        policyService.endBySeller(1L, policyId)
        assertEquals("ENDED", policyRepository.findById(policyId)!!.status.name)
    }
}
