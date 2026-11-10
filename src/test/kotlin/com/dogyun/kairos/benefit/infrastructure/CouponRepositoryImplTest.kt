package com.dogyun.kairos.benefit.infrastructure

import com.dogyun.kairos.CommerceTestSupport
import com.dogyun.kairos.benefit.domain.Coupon
import com.dogyun.kairos.benefit.domain.CouponPolicy
import com.dogyun.kairos.benefit.domain.CouponPolicyRepository
import com.dogyun.kairos.benefit.domain.CouponRepository
import com.dogyun.kairos.benefit.domain.CouponStatus
import com.dogyun.kairos.benefit.domain.CouponType
import com.dogyun.kairos.benefit.domain.DiscountType
import com.dogyun.kairos.benefit.domain.IssuerType
import com.dogyun.kairos.benefit.domain.TargetType
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CouponRepositoryImplTest : CommerceTestSupport() {

    @Autowired
    private lateinit var policyRepository: CouponPolicyRepository

    @Autowired
    private lateinit var couponRepository: CouponRepository

    private val now = LocalDateTime.now().withNano(0)

    private fun savePolicy(totalQuantity: Int? = null): Long = requireNotNull(
        policyRepository.save(
            CouponPolicy.create(
                name = "쿠폰", issuerType = IssuerType.PLATFORM, sellerId = null, couponType = CouponType.PRODUCT,
                targetType = TargetType.ALL_PRODUCTS, targetProductId = null, discountType = DiscountType.FIXED,
                discountValue = 1000, maxDiscount = null, minOrderAmount = null, totalQuantity = totalQuantity,
                validityDays = null, validFrom = now.minusDays(1), validUntil = now.plusDays(30),
            ),
        ).id,
    )

    private fun saveCoupon(policyId: Long, memberId: Long, expiresAt: LocalDateTime = now.plusDays(10)): Long = requireNotNull(
        couponRepository.save(Coupon(null, policyId, memberId, CouponStatus.AVAILABLE, null, now, expiresAt, null)).id,
    )

    @Test
    fun tryIncreaseIssued는_total_quantity_NULL이면_항상_true다() {
        val policyId = savePolicy(totalQuantity = null)
        repeat(3) { assertTrue(policyRepository.tryIncreaseIssued(policyId)) }
        assertEquals(3, policyRepository.findById(policyId)!!.issuedCount)
    }

    @Test
    fun tryIncreaseIssued는_수량이_차면_false다() {
        val policyId = savePolicy(totalQuantity = 2)
        assertTrue(policyRepository.tryIncreaseIssued(policyId))
        assertTrue(policyRepository.tryIncreaseIssued(policyId))
        assertFalse(policyRepository.tryIncreaseIssued(policyId))
        assertEquals(2, policyRepository.findById(policyId)!!.issuedCount)
    }

    @Test
    fun 정책_상태_저장은_발급_수량을_덮어쓰지_않는다() {
        val policyId = savePolicy(totalQuantity = 5)
        val stale = policyRepository.findById(policyId)!!
        policyRepository.tryIncreaseIssued(policyId)
        stale.end()
        policyRepository.save(stale)
        val reloaded = policyRepository.findById(policyId)!!
        assertEquals(1, reloaded.issuedCount)
        assertEquals("ENDED", reloaded.status.name)
    }

    @Test
    fun markUsed는_AVAILABLE이고_본인이고_만료_전일_때만_true다() {
        val policyId = savePolicy()
        val couponId = saveCoupon(policyId, memberId = 1L)
        val expired = saveCoupon(savePolicy(), memberId = 1L, expiresAt = now.minusSeconds(1))

        assertFalse(couponRepository.markUsed(couponId, 2L, now))
        assertFalse(couponRepository.markUsed(expired, 1L, now))
        assertTrue(couponRepository.markUsed(couponId, 1L, now))
        assertFalse(couponRepository.markUsed(couponId, 1L, now))
        assertEquals(CouponStatus.USED, couponRepository.findById(couponId)!!.status)
    }

    @Test
    fun restoreIfNotExpired는_만료된_쿠폰을_돌리지_않는다() {
        val fresh = saveCoupon(savePolicy(), memberId = 1L, expiresAt = now.plusMinutes(5))
        val stale = saveCoupon(savePolicy(), memberId = 1L, expiresAt = now.plusMinutes(5))
        couponRepository.markUsed(fresh, 1L, now)
        couponRepository.markUsed(stale, 1L, now)
        couponRepository.attachOrder(listOf(fresh, stale), 77L)
        assertEquals(2, couponRepository.findUsedByOrderId(77L).size)

        assertTrue(couponRepository.restoreIfNotExpired(fresh, now.plusMinutes(1)))
        assertFalse(couponRepository.restoreIfNotExpired(stale, now.plusMinutes(6)))

        val restored = couponRepository.findById(fresh)!!
        assertEquals(CouponStatus.AVAILABLE, restored.status)
        assertNull(restored.usedOrderId)
        assertNull(restored.usedAt)
        assertEquals(CouponStatus.USED, couponRepository.findById(stale)!!.status)
        assertNotNull(couponRepository.findById(stale)!!.usedOrderId)
    }

    @Test
    fun 내_쿠폰_상태_필터는_계산값으로_걸린다() {
        val available = saveCoupon(savePolicy(), 1L)
        val expired = saveCoupon(savePolicy(), 1L, expiresAt = now.minusDays(1))
        val used = saveCoupon(savePolicy(), 1L)
        couponRepository.markUsed(used, 1L, now)

        fun ids(status: String?) = couponRepository.findPageByMember(1L, status, now, 0, 20).content.map { it.id }.toSet()
        assertEquals(setOf(available, expired, used), ids(null))
        assertEquals(setOf(available), ids("AVAILABLE"))
        assertEquals(setOf(expired), ids("EXPIRED"))
        assertEquals(setOf(used), ids("USED"))
    }
}
