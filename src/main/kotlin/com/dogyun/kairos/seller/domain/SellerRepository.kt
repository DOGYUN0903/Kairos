package com.dogyun.kairos.seller.domain

import com.dogyun.kairos.common.domain.ListOrder
import com.dogyun.kairos.common.domain.PageResult

interface SellerRepository {
    fun save(seller: Seller): Seller
    fun findById(id: SellerId): Seller?
    fun findAllByIds(ids: Collection<SellerId>): List<Seller>
    fun findByMemberId(memberId: Long): Seller?
    fun existsByBusinessNumber(businessNumber: BusinessNumber): Boolean

    /** status 가 null 이면 전체. id 기준 정렬(LATEST 내림차순, OLDEST 오름차순). */
    fun findPage(status: SellerStatus?, order: ListOrder, page: Int, size: Int): PageResult<Seller>
}
