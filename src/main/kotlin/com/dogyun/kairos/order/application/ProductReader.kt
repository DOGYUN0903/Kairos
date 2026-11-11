package com.dogyun.kairos.order.application

/** order 가 catalog 를 보는 창구. 구현은 order.infrastructure 에서 catalog 의 SkuQueryService 로 한다. */
interface ProductReader {
    /** 없는 SKU ID 는 결과에서 빠진다. */
    fun findOrderableSkus(skuIds: Collection<Long>): Map<Long, OrderableSkuInfo>

    /** 상품 ID 목록 → 대표 이미지 URL, 한 번의 조회. 없는·삭제된 상품과 이미지 없는 상품은 결과에서 빠진다. */
    fun findThumbnailUrls(productIds: Collection<Long>): Map<Long, String>
}

data class OrderableSkuInfo(
    val skuId: Long,
    val productId: Long,
    val productName: String,
    val brandName: String,
    val optionDisplayName: String,
    val salePrice: Long,
    val extraPrice: Long,
    val sellerId: Long,
    /** 지금 주문할 수 있는 상태(AVAILABLE)인가. */
    val available: Boolean,
)
