package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.catalog.application.SkuAvailability
import com.dogyun.kairos.catalog.application.SkuQueryService
import com.dogyun.kairos.order.application.OrderableSkuInfo
import com.dogyun.kairos.order.application.ProductReader
import org.springframework.stereotype.Component

/** order 의 [ProductReader] 를 catalog 의 [SkuQueryService] 로 구현한다. */
// 빈 이름을 명시한다: cart·catalog 에도 같은 클래스 이름이 있다.
@Component("orderProductReader")
class ProductReaderImpl(private val skuQueryService: SkuQueryService) : ProductReader {

    override fun findThumbnailUrls(productIds: Collection<Long>): Map<Long, String> =
        skuQueryService.findThumbnailUrls(productIds)

    override fun findOrderableSkus(skuIds: Collection<Long>): Map<Long, OrderableSkuInfo> =
        skuQueryService.findOrderableSkus(skuIds).mapValues { (_, sku) ->
            OrderableSkuInfo(
                skuId = sku.skuId,
                productId = sku.productId,
                productName = sku.productName,
                brandName = sku.brandName,
                optionDisplayName = sku.optionDisplayName,
                salePrice = sku.salePrice,
                extraPrice = sku.extraPrice,
                sellerId = sku.sellerId,
                thumbnailUrl = sku.thumbnailUrl,
                available = sku.availability == SkuAvailability.AVAILABLE,
            )
        }
}
