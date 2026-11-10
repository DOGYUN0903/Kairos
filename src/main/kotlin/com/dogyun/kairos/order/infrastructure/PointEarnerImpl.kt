package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.benefit.application.PointService
import com.dogyun.kairos.order.application.PointEarner
import org.springframework.stereotype.Component

/** order 의 [PointEarner] 를 benefit 의 [PointService.earnForPurchase] 로 구현한다. */
@Component
class PointEarnerImpl(private val pointService: PointService) : PointEarner {

    override fun earnForPurchase(memberId: Long, sellerOrderId: Long, paidAmount: Long): Long =
        pointService.earnForPurchase(memberId, sellerOrderId, paidAmount)
}
