package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.Product
import java.time.LocalDateTime

data class ProductImageResult(val fileId: Long, val url: String, val isMain: Boolean)

data class OptionValueResult(val valueId: Long, val name: String)

data class OptionResult(val optionId: Long, val name: String, val values: List<OptionValueResult>)

data class SellerSkuResult(
    val skuId: Long,
    val optionValueIds: List<Long>,
    /** 옵션 순서대로 값 이름을 " / " 로 이은 표시명(예 "블랙 / L"). */
    val displayName: String,
    val extraPrice: Long,
    val stock: Int,
    val onSale: Boolean,
    val skuStatus: String,
)

data class SellerProductDetail(
    val productId: Long,
    val sellerId: Long,
    val brandId: Long,
    val brandName: String,
    val categoryId: Long,
    val name: String,
    val description: String,
    val images: List<ProductImageResult>,
    val originalPrice: Long,
    val salePrice: Long,
    val discountRate: Int,
    val status: String,
    val rejectReason: String?,
    val onSaleAt: LocalDateTime?,
    val salesCount: Long,
    val soldOut: Boolean,
    val options: List<OptionResult>,
    /** ACTIVE·DISCONTINUED 모두. */
    val skus: List<SellerSkuResult>,
    /** 대기 중인 변경 요청 ID. 없으면 null. */
    val pendingChangeRequestId: Long?,
)

/** 변경 요청이 담은 옵션. ID 가 있으면 기존 옵션·값을 유지하는 변경이다. */
data class ChangeOptionValueResult(val valueId: Long?, val name: String)

data class ChangeOptionResult(val optionId: Long?, val name: String, val values: List<ChangeOptionValueResult>)

data class ProductContentResult(
    val name: String,
    val description: String,
    val categoryId: Long,
    val images: List<ProductImageResult>,
    val options: List<ChangeOptionResult>,
)

data class ChangeRequestDetail(
    val changeRequestId: Long,
    val productId: Long,
    val sellerId: Long,
    val status: String,
    val rejectReason: String?,
    /** 요청한 정가. */
    val originalPrice: Long,
    val content: ProductContentResult,
)

data class ChangeRequestSummary(
    val changeRequestId: Long,
    val productId: Long,
    val sellerId: Long,
    /** 요청한 상품명. */
    val name: String,
    val originalPrice: Long,
    val status: String,
    val requestedAt: LocalDateTime,
    val sellerName: String,
)

data class SellerProductSummary(
    val productId: Long,
    val name: String,
    val thumbnailUrl: String,
    val brandName: String,
    val salePrice: Long,
    val status: String,
    val soldOut: Boolean,
    /** ACTIVE SKU 재고 합. */
    val stockTotal: Int,
    val createdAt: LocalDateTime,
    val hasPendingChangeRequest: Boolean,
)

internal fun toSellerProductDetail(
    product: Product,
    brandName: String,
    imageUrls: Map<Long, String>,
    pendingChangeRequestId: Long?,
): SellerProductDetail {
    val content = product.content
    val valueNames = content.options.flatMap { it.values }.associate { requireNotNull(it.id) to it.name }
    return SellerProductDetail(
        productId = requireNotNull(product.id).value,
        sellerId = product.sellerId,
        brandId = product.brandId,
        brandName = brandName,
        categoryId = content.categoryId,
        name = content.name,
        description = content.description,
        images = content.imageFileIds.mapIndexed { index, fileId ->
            ProductImageResult(fileId, imageUrls[fileId].orEmpty(), isMain = index == 0)
        },
        originalPrice = product.originalPrice,
        salePrice = product.salePrice,
        discountRate = product.discountRate,
        status = product.status.name,
        rejectReason = product.rejectReason,
        onSaleAt = product.onSaleAt,
        salesCount = product.salesCount,
        soldOut = product.soldOut,
        options = content.options.map { option ->
            OptionResult(
                requireNotNull(option.id), option.name,
                option.values.map { OptionValueResult(requireNotNull(it.id), it.name) },
            )
        },
        skus = product.skus.map { sku ->
            SellerSkuResult(
                skuId = requireNotNull(sku.id).value,
                optionValueIds = sku.optionValueIds,
                displayName = skuDisplayName(sku.optionValueIds, content.options.flatMap { it.values }.mapNotNull { it.id }, valueNames),
                extraPrice = sku.extraPrice,
                stock = sku.stock,
                onSale = sku.onSale,
                skuStatus = sku.skuStatus.name,
            )
        },
        pendingChangeRequestId = pendingChangeRequestId,
    )
}

/**
 * 옵션 순서대로 값 이름을 잇는다. DISCONTINUED SKU 가 참조하던 값이 콘텐츠에서 사라졌으면
 * 이름을 알 수 없으므로 "(삭제된 값)" 으로 표시한다.
 */
internal fun skuDisplayName(skuValueIds: List<Long>, orderedValueIds: List<Long>, valueNames: Map<Long, String>): String {
    val known = orderedValueIds.filter { it in skuValueIds }.map { valueNames.getValue(it) }
    val unknown = skuValueIds.filter { it !in valueNames }.map { "(삭제된 값)" }
    return (known + unknown).joinToString(" / ")
}

internal fun toSellerProductSummary(
    product: Product,
    brandName: String,
    thumbnailUrl: String,
    hasPendingChangeRequest: Boolean,
) = SellerProductSummary(
    productId = requireNotNull(product.id).value,
    name = product.content.name,
    thumbnailUrl = thumbnailUrl,
    brandName = brandName,
    salePrice = product.salePrice,
    status = product.status.name,
    soldOut = product.soldOut,
    stockTotal = product.activeSkus.sumOf { it.stock },
    createdAt = requireNotNull(product.createdAt) { "저장소에서 읽은 상품에는 등록 일시가 있어야 합니다" },
    hasPendingChangeRequest = hasPendingChangeRequest,
)
