package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.deal.application.DealQueryService
import com.dogyun.kairos.order.application.DealReader
import com.dogyun.kairos.order.application.DealSnapshot
import org.springframework.stereotype.Component

/** deal 모듈의 application 서비스를 호출한다. Phase 4 에서는 HTTP/gRPC 클라이언트로 교체된다. */
@Component
class DealReaderImpl(private val dealQueryService: DealQueryService) : DealReader {

    override fun getProductNames(dealIds: Collection<Long>): Map<Long, String> = dealQueryService.getProductNames(dealIds)

    override fun getSnapshot(dealId: Long): DealSnapshot {
        val s = dealQueryService.getSnapshot(dealId)
        return DealSnapshot(dealId = s.dealId, dealPrice = s.dealPrice, perMemberLimit = s.perMemberLimit, ongoing = s.ongoing, totalQuantity = s.totalQuantity)
    }
}
