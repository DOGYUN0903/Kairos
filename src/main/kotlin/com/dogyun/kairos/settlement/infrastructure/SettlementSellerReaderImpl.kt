package com.dogyun.kairos.settlement.infrastructure

import com.dogyun.kairos.seller.application.SellerQueryService
import com.dogyun.kairos.settlement.application.SettlementSellerInfo
import com.dogyun.kairos.settlement.application.SettlementSellerReader
import org.springframework.stereotype.Component

/** seller 모듈의 application 서비스만 호출한다. */
@Component
class SettlementSellerReaderImpl(private val sellerQueryService: SellerQueryService) : SettlementSellerReader {

    override fun findSellers(sellerIds: Collection<Long>): Map<Long, SettlementSellerInfo> =
        sellerQueryService.findSettlementSnapshots(sellerIds).mapValues { (id, s) ->
            SettlementSellerInfo(id, s.companyName, s.sellerStatus)
        }
}
