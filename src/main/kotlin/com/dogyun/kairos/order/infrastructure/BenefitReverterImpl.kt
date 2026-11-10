package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.benefit.application.CouponService
import com.dogyun.kairos.benefit.application.PointService
import com.dogyun.kairos.order.application.BenefitReverter
import org.springframework.stereotype.Component

/** order 의 [BenefitReverter] 를 benefit 의 [PointService]·[CouponService] 로 구현한다. 락 순서: `member_point` → `coupon`. */
@Component
class BenefitReverterImpl(
    private val couponService: CouponService,
    private val pointService: PointService,
) : BenefitReverter {

    override fun revertAll(orderId: Long, memberId: Long, couponIds: Collection<Long>, pointsUsed: Long) {
        pointService.refundForOrder(memberId, orderId, pointsUsed)
        couponService.restoreCoupons(couponIds)
    }

    override fun revertItem(orderId: Long, memberId: Long, orderItemId: Long, pointsRefund: Long, productCouponId: Long?, cartCouponId: Long?) {
        pointService.refundForOrderItem(memberId, orderId, orderItemId, pointsRefund)
        couponService.restoreCoupons(listOfNotNull(productCouponId, cartCouponId))
    }
}
