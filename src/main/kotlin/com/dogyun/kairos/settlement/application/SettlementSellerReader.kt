package com.dogyun.kairos.settlement.application

/** 정산 화면·생성에 쓰는 판매자 정보 읽기 포트. 구현은 seller.application 을 호출한다(infrastructure). */
interface SettlementSellerReader {
    fun findSellers(sellerIds: Collection<Long>): Map<Long, SettlementSellerInfo>
}

data class SettlementSellerInfo(val sellerId: Long, val companyName: String, val sellerStatus: String)
