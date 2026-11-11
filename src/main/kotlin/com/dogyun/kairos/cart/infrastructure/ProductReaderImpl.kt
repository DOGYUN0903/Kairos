package com.dogyun.kairos.cart.infrastructure

import com.dogyun.kairos.cart.application.CartSkuInfo
import com.dogyun.kairos.cart.application.ProductReader
import com.dogyun.kairos.catalog.application.SkuQueryService
import org.springframework.stereotype.Component

/** cart 의 [ProductReader] 를 catalog 의 [SkuQueryService] 로 구현한다. */
@Component
class ProductReaderImpl(private val skuQueryService: SkuQueryService) : ProductReader {

    override fun findOrderableSkus(skuIds: Collection<Long>): Map<Long, CartSkuInfo> =
        skuQueryService.findOrderableSkus(skuIds).mapValues { (_, sku) ->
            CartSkuInfo(
                skuId = sku.skuId,
                productId = sku.productId,
                sellerId = sku.sellerId,
                productName = sku.productName,
                brandName = sku.brandName,
                thumbnailUrl = sku.thumbnailUrl,
                optionDisplayName = sku.optionDisplayName,
                unitPrice = sku.salePrice + sku.extraPrice,
                availability = sku.availability.name,
            )
        }
}
