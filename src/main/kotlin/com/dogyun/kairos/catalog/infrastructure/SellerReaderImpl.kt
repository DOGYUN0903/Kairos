package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.catalog.application.BrandSnapshot
import com.dogyun.kairos.catalog.application.SellerReader
import com.dogyun.kairos.seller.application.BrandQueryService
import org.springframework.stereotype.Component

/** catalog 의 [SellerReader] 를 seller 모듈의 조회 서비스로 구현한다. */
@Component("catalogSellerReader") // seller 모듈의 같은 이름 클래스와 빈 이름이 겹치지 않도록 한다.
class SellerReaderImpl(private val brandQueryService: BrandQueryService) : SellerReader {

    override fun findBrand(brandId: Long): BrandSnapshot? =
        brandQueryService.findSnapshot(brandId)?.let { BrandSnapshot(it.brandId, it.sellerId, it.name, it.approved) }

    override fun getBrandNames(brandIds: Collection<Long>): Map<Long, String> = brandQueryService.getNames(brandIds)
}
