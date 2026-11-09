package com.dogyun.kairos.catalog.application

enum class SkuAvailability { AVAILABLE, SOLD_OUT, PAUSED, DISCONTINUED, ENDED }

/** 주문·장바구니가 보는 SKU 스냅샷. 도메인 타입을 노출하지 않는다. */
data class OrderableSku(
    val skuId: Long,
    val productId: Long,
    val productName: String,
    val brandId: Long,
    val brandName: String,
    val thumbnailUrl: String,
    /** 옵션 순서대로 값 이름을 " / " 로 이은 표시명. 옵션이 없으면 빈 문자열. */
    val optionDisplayName: String,
    val salePrice: Long,
    val extraPrice: Long,
    val sellerId: Long,
    val availability: SkuAvailability,
)

data class SkuQuantity(val skuId: Long, val quantity: Int)

data class ProductQuantity(val productId: Long, val quantity: Int)
