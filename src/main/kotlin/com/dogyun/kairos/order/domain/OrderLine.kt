package com.dogyun.kairos.order.domain

/** 주문 생성 입력. 주문 시점의 상품·판매자 정보(스냅샷 원본)와 수량. */
data class OrderLine(
    val skuId: Long,
    val productId: Long,
    val productName: String,
    val brandName: String,
    val optionDisplayName: String,
    val salePrice: Long,
    val extraPrice: Long,
    val sellerId: Long,
    val sellerName: String,
    val commissionRateBp: Int,
    val quantity: Int,
)
