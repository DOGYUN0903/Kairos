package com.dogyun.kairos.benefit.infrastructure

import com.dogyun.kairos.benefit.application.BenefitProductReader
import com.dogyun.kairos.benefit.application.ProductRef
import com.dogyun.kairos.catalog.application.ProductQueryService
import org.springframework.stereotype.Component

/** benefit 의 [BenefitProductReader] 를 catalog 의 [ProductQueryService] 로 구현한다. */
@Component
class BenefitProductReaderImpl(private val productQueryService: ProductQueryService) : BenefitProductReader {

    override fun findProduct(productId: Long): ProductRef? =
        productQueryService.findProductRef(productId)?.let { ProductRef(it.productId, it.sellerId) }
}
