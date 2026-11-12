package com.dogyun.kairos.cart.application

/** cart 가 catalog 를 보는 창구. 구현은 cart.infrastructure 에서 catalog 의 SkuQueryService 로 한다. */
interface ProductReader {
    /** 없는 SKU ID 는 결과에서 빠진다. */
    fun findOrderableSkus(skuIds: Collection<Long>): Map<Long, CartSkuInfo>
}

data class CartSkuInfo(
    val skuId: Long,
    val productId: Long,
    val sellerId: Long,
    val productName: String,
    val brandName: String,
    val thumbnailUrl: String,
    val optionDisplayName: String,
    /** salePrice + extraPrice */
    val unitPrice: Long,
    /** AVAILABLE, SOLD_OUT, PAUSED, DISCONTINUED, ENDED */
    val availability: String,
)
