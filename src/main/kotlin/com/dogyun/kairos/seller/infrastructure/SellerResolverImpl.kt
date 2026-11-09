package com.dogyun.kairos.seller.infrastructure

import com.dogyun.kairos.common.security.SellerPrincipal
import com.dogyun.kairos.common.security.SellerResolver
import com.dogyun.kairos.seller.application.SellerQueryService
import org.springframework.stereotype.Component

/** common 의 [SellerResolver] 를 seller 모듈의 조회 서비스로 구현한다. */
@Component
class SellerResolverImpl(private val sellerQueryService: SellerQueryService) : SellerResolver {

    override fun resolve(memberId: Long): SellerPrincipal? =
        sellerQueryService.findSnapshotByMemberId(memberId)?.let {
            SellerPrincipal(it.sellerId, it.memberId, it.canManage, it.canView)
        }
}
