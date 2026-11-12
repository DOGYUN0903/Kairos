package com.dogyun.kairos.cart.infrastructure

import com.dogyun.kairos.cart.application.CartSellerInfo
import com.dogyun.kairos.cart.application.SellerReader
import com.dogyun.kairos.seller.application.SellerQueryService
import org.springframework.stereotype.Component

/**
 * cart 의 [SellerReader] 를 seller 의 [SellerQueryService] 로 구현한다.
 * `orderable` 은 order 의 SellerReaderImpl 과 같은 기준(`canManage`)이라 견적·주문과 어긋나지 않는다.
 */
// 빈 이름을 명시한다: order·catalog 에도 같은 클래스 이름이 있다.
@Component("cartSellerReader")
class SellerReaderImpl(private val sellerQueryService: SellerQueryService) : SellerReader {

    override fun findSellers(sellerIds: Collection<Long>): Map<Long, CartSellerInfo> =
        sellerQueryService.findSettlementSnapshots(sellerIds)
            .mapValues { (_, s) -> CartSellerInfo(s.sellerId, s.companyName, s.canManage) }
}
