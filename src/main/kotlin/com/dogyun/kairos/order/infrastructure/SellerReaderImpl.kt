package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.order.application.SellerInfo
import com.dogyun.kairos.order.application.SellerReader
import com.dogyun.kairos.seller.application.SellerQueryService
import org.springframework.stereotype.Component

/** order 의 [SellerReader] 를 seller 의 [SellerQueryService] 로 구현한다. 영업할 수 없는(정지 등) 판매자는 뺀다. */
// 빈 이름을 명시한다: cart·catalog 에도 같은 클래스 이름이 있다.
@Component("orderSellerReader")
class SellerReaderImpl(private val sellerQueryService: SellerQueryService) : SellerReader {

    override fun findSellers(sellerIds: Collection<Long>): Map<Long, SellerInfo> =
        sellerQueryService.findSettlementSnapshots(sellerIds)
            .filterValues { it.canManage }
            .mapValues { (_, s) -> SellerInfo(s.sellerId, s.companyName, s.commissionRateBp) }
}
