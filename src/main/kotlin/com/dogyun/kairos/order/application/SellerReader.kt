package com.dogyun.kairos.order.application

/** order 가 seller 를 보는 창구. 구현은 order.infrastructure 에서 seller 의 SellerQueryService 로 한다. */
interface SellerReader {
    /** 없거나 영업할 수 없는(정지 등) 판매자는 결과에서 빠진다. */
    fun findSellers(sellerIds: Collection<Long>): Map<Long, SellerInfo>
}

data class SellerInfo(val sellerId: Long, val companyName: String, val commissionRateBp: Int)
