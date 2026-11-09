package com.dogyun.kairos.seller.domain

import com.dogyun.kairos.common.domain.PageResult

interface SellerRepository {
    fun save(seller: Seller): Seller
    fun findById(id: SellerId): Seller?
    fun findByMemberId(memberId: Long): Seller?
    fun existsByBusinessNumber(businessNumber: BusinessNumber): Boolean

    /** status 가 null 이면 전체. 최신 등록순. */
    fun findPage(status: SellerStatus?, page: Int, size: Int): PageResult<Seller>
}
