package com.dogyun.kairos.seller.domain

import com.dogyun.kairos.common.domain.ListOrder
import com.dogyun.kairos.common.domain.PageResult

interface BrandRepository {
    fun save(brand: Brand): Brand
    fun findById(id: BrandId): Brand?
    fun existsByName(name: String): Boolean
    fun findAllBySellerId(sellerId: Long): List<Brand>

    /** status 가 null 이면 전체. id 기준 정렬(LATEST 내림차순, OLDEST 오름차순). */
    fun findPage(status: BrandStatus?, order: ListOrder, page: Int, size: Int): PageResult<Brand>
    fun findAllByIds(ids: Collection<Long>): List<Brand>

    /** APPROVED 브랜드를 이름 오름차순으로 최대 [limit] 개. 공개 브랜드 목록용이다. */
    fun findApprovedOrderByName(limit: Int): List<Brand>
}
