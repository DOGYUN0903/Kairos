package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.benefit.application.BenefitProperties
import com.dogyun.kairos.benefit.application.CouponService
import com.dogyun.kairos.benefit.application.CouponTermsResult
import com.dogyun.kairos.benefit.application.PointService
import com.dogyun.kairos.order.application.BenefitApplier
import com.dogyun.kairos.order.application.BenefitReservation
import com.dogyun.kairos.order.application.ReservedCoupon
import com.dogyun.kairos.order.application.UsableCoupon
import com.dogyun.kairos.order.domain.PointUsePolicy
import org.springframework.stereotype.Component

/** order 의 [BenefitApplier] 를 benefit 의 [CouponService]·[PointService] 로 구현하고 타입을 번역한다. */
@Component
class BenefitApplierImpl(
    private val couponService: CouponService,
    private val pointService: PointService,
    private val benefitProperties: BenefitProperties,
) : BenefitApplier {

    override fun quote(memberId: Long, couponIds: Collection<Long>): BenefitReservation =
        reservation(couponService.quoteCoupons(memberId, couponIds), pointService.balanceOf(memberId))

    override fun listUsable(memberId: Long): List<UsableCoupon> =
        couponService.listUsableCoupons(memberId).map { UsableCoupon(it.terms.toReserved(), it.name, it.expiresAt) }

    /** 락 순서: `member_point`(lockBalance) → `coupon`(조건부 UPDATE). */
    override fun reserve(memberId: Long, couponIds: Collection<Long>): BenefitReservation {
        val balance = pointService.lockBalance(memberId)
        return reservation(couponService.reserveCoupons(memberId, couponIds), balance)
    }

    override fun commit(memberId: Long, orderId: Long, couponIds: Collection<Long>, pointsToUse: Long) {
        couponService.attachOrder(couponIds, orderId)
        pointService.use(memberId, orderId, pointsToUse)
    }

    private fun reservation(coupons: List<CouponTermsResult>, balance: Long) = BenefitReservation(
        coupons = coupons.associate { it.couponId to it.toReserved() },
        pointBalance = balance,
        pointPolicy = PointUsePolicy(benefitProperties.minUsableBalance, benefitProperties.useUnit, benefitProperties.maxUseRateBp),
    )

    private fun CouponTermsResult.toReserved() = ReservedCoupon(
        couponId, policyId, couponType, issuerType, targetType, targetProductId, sellerId, discountType, discountValue,
        maxDiscount, minOrderAmount,
    )
}
