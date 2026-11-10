package com.dogyun.kairos.seller.application

data class SellerDetail(
    val sellerId: Long,
    val memberId: Long,
    val companyName: String,
    val businessNumber: String,
    val representativeName: String,
    val phone: String,
    val bank: String,
    val accountNumber: String,
    val holder: String,
    val commissionRateBp: Int,
    val status: String,
    val rejectReason: String?,
    val suspendReason: String?,
)

/** 다른 모듈·인자 해석기가 쓰는 판매자 요약. 도메인 타입을 노출하지 않는다. */
data class SellerSnapshot(
    val sellerId: Long,
    val memberId: Long,
    val canManage: Boolean,
    val canView: Boolean,
)

/** 주문 시점에 굳히는 정산 정보. 도메인 타입을 노출하지 않는다. */
data class SellerSettlementSnapshot(
    val sellerId: Long,
    val companyName: String,
    val commissionRateBp: Int,
    val canManage: Boolean,
)
