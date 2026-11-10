package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.Product
import com.dogyun.kairos.catalog.domain.ProductListRow

data class ProductSummary(
    val productId: Long,
    val thumbnailUrl: String,
    val brandName: String,
    val name: String,
    val originalPrice: Long,
    val salePrice: Long,
    val discountRate: Int,
    val soldOut: Boolean,
    val likeCount: Long,
    val reviewCount: Long,
    /** 소수 첫째 자리, 후기가 없으면 0.0. */
    val averageRating: Double,
    /** 로그인한 요청이면 내 좋아요 여부, 비로그인이면 null. */
    val liked: Boolean?,
)

/** 사용자에게 보이는 SKU. 재고 숫자는 노출하지 않고 품절 여부만 준다. */
data class PublicSkuResult(
    val skuId: Long,
    val optionValueIds: List<Long>,
    val displayName: String,
    val extraPrice: Long,
    val soldOut: Boolean,
)

data class ProductDetail(
    val productId: Long,
    val brandId: Long,
    val brandName: String,
    val categoryId: Long,
    val name: String,
    val description: String,
    /** 표시 순서대로. 첫 번째가 대표 이미지. */
    val imageUrls: List<String>,
    val originalPrice: Long,
    val salePrice: Long,
    val discountRate: Int,
    val soldOut: Boolean,
    val options: List<OptionResult>,
    /** ACTIVE SKU 만. */
    val skus: List<PublicSkuResult>,
    val likeCount: Long,
    val reviewCount: Long,
    val averageRating: Double,
    val liked: Boolean?,
)

internal fun averageRatingOf(reviewCount: Long, ratingSum: Long): Double =
    if (reviewCount == 0L) 0.0 else Math.round(ratingSum * 10.0 / reviewCount) / 10.0

internal fun toProductSummary(row: ProductListRow, thumbnailUrl: String, liked: Boolean?) = ProductSummary(
    productId = row.productId,
    thumbnailUrl = thumbnailUrl,
    brandName = row.brandName,
    name = row.name,
    originalPrice = row.originalPrice,
    salePrice = row.salePrice,
    discountRate = ((row.originalPrice - row.salePrice) * 100 / row.originalPrice).toInt(),
    soldOut = row.soldOut,
    likeCount = row.likeCount,
    reviewCount = row.reviewCount,
    averageRating = averageRatingOf(row.reviewCount, row.ratingSum),
    liked = liked,
)

internal fun toProductDetail(product: Product, brandName: String, imageUrls: Map<Long, String>, liked: Boolean?): ProductDetail {
    val content = product.content
    val orderedValues = content.options.flatMap { it.values }
    val valueNames = orderedValues.associate { requireNotNull(it.id) to it.name }
    return ProductDetail(
        productId = requireNotNull(product.id).value,
        brandId = product.brandId,
        brandName = brandName,
        categoryId = content.categoryId,
        name = content.name,
        description = content.description,
        imageUrls = content.imageFileIds.map { imageUrls[it].orEmpty() },
        originalPrice = product.originalPrice,
        salePrice = product.salePrice,
        discountRate = product.discountRate,
        soldOut = product.soldOut,
        options = content.options.map { option ->
            OptionResult(requireNotNull(option.id), option.name, option.values.map { OptionValueResult(requireNotNull(it.id), it.name) })
        },
        skus = product.activeSkus.map { sku ->
            PublicSkuResult(
                skuId = requireNotNull(sku.id).value,
                optionValueIds = sku.optionValueIds,
                // ACTIVE SKU 의 값은 모두 현재 콘텐츠에 있으므로 옵션 순서대로 이름을 찾을 수 있다.
                displayName = orderedValues.filter { requireNotNull(it.id) in sku.optionValueIds }.joinToString(" / ") { valueNames.getValue(requireNotNull(it.id)) },
                extraPrice = sku.extraPrice,
                soldOut = !sku.available,
            )
        },
        likeCount = product.likeCount,
        reviewCount = product.reviewCount,
        averageRating = product.averageRating,
        liked = liked,
    )
}
