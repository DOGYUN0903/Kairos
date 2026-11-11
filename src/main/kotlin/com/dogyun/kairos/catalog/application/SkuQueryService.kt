package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.Product
import com.dogyun.kairos.catalog.domain.ProductRepository
import com.dogyun.kairos.catalog.domain.ProductStatus
import com.dogyun.kairos.catalog.domain.Sku
import com.dogyun.kairos.catalog.domain.SkuStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SkuQueryService(
    private val productRepository: ProductRepository,
    private val sellerReader: SellerReader,
    private val imageFileReader: ImageFileReader,
) {

    /** 없는 SKU ID 는 결과에서 빠진다. DISCONTINUED SKU 도 담고 availability 로 구분한다. */
    @Transactional(readOnly = true)
    fun findOrderableSkus(skuIds: Collection<Long>): Map<Long, OrderableSku> {
        if (skuIds.isEmpty()) return emptyMap()
        val wanted = skuIds.toSet()
        val products = productRepository.findAllBySkuIds(wanted)
        val brandNames = sellerReader.getBrandNames(products.map { it.brandId }.toSet())
        val thumbnails = imageFileReader.findImages(products.mapNotNull { it.content.imageFileIds.firstOrNull() }.toSet())
        return products.flatMap { product ->
            val valueIds = product.content.options.flatMap { it.values }.mapNotNull { it.id }
            val valueNames = product.content.options.flatMap { it.values }.associate { requireNotNull(it.id) to it.name }
            product.skus.filter { requireNotNull(it.id).value in wanted }.map { sku ->
                val skuId = requireNotNull(sku.id).value
                skuId to OrderableSku(
                    skuId = skuId,
                    productId = requireNotNull(product.id).value,
                    productName = product.content.name,
                    brandId = product.brandId,
                    brandName = brandNames[product.brandId].orEmpty(),
                    thumbnailUrl = product.content.imageFileIds.firstOrNull()?.let { thumbnails[it]?.url }.orEmpty(),
                    optionDisplayName = skuDisplayName(sku.optionValueIds, valueIds, valueNames),
                    salePrice = product.salePrice,
                    extraPrice = sku.extraPrice,
                    sellerId = product.sellerId,
                    availability = availability(product, sku),
                )
            }
        }.toMap()
    }

    /**
     * 상품의 대표 이미지(첫 이미지) URL 을 한 번에 조회한다. 없거나 삭제된 상품, 이미지가 없는 상품은 결과에서 빠진다.
     * [findOrderableSkus] 의 `thumbnailUrl` 과 같은 규칙이다.
     */
    @Transactional(readOnly = true)
    fun findThumbnailUrls(productIds: Collection<Long>): Map<Long, String> {
        if (productIds.isEmpty()) return emptyMap()
        val products = productRepository.findAllByIds(productIds.toSet())
        val thumbnails = imageFileReader.findImages(products.mapNotNull { it.content.imageFileIds.firstOrNull() }.toSet())
        return products.mapNotNull { product ->
            val url = product.content.imageFileIds.firstOrNull()?.let { thumbnails[it]?.url } ?: return@mapNotNull null
            requireNotNull(product.id).value to url
        }.toMap()
    }

    /** 우선순위: 상품 종료 > SKU 단종 > 판매 중지(상품 비판매·SKU 비판매) > 품절. */
    private fun availability(product: Product, sku: Sku): SkuAvailability = when {
        product.status == ProductStatus.ENDED -> SkuAvailability.ENDED
        sku.skuStatus == SkuStatus.DISCONTINUED -> SkuAvailability.DISCONTINUED
        product.status != ProductStatus.ON_SALE || !sku.onSale -> SkuAvailability.PAUSED
        sku.stock <= 0 -> SkuAvailability.SOLD_OUT
        else -> SkuAvailability.AVAILABLE
    }
}
