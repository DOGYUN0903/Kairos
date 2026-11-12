package com.dogyun.kairos.cart.application

/** cart 가 seller 를 보는 창구. 구현은 cart.infrastructure 에서 seller 의 SellerQueryService 로 한다. */
interface SellerReader {
    /** 없는 판매자 ID 는 결과에서 빠진다. */
    fun findSellers(sellerIds: Collection<Long>): Map<Long, CartSellerInfo>
}

data class CartSellerInfo(
    val sellerId: Long,
    val companyName: String,
    /** 견적·주문이 받아 주는 판매자(활성)인지 */
    val orderable: Boolean,
)
