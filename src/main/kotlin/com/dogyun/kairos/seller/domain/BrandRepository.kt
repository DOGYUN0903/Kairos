package com.dogyun.kairos.seller.domain

import com.dogyun.kairos.common.domain.PageResult

interface BrandRepository {
    fun save(brand: Brand): Brand
    fun findById(id: BrandId): Brand?
    fun existsByName(name: String): Boolean
    fun findAllBySellerId(sellerId: Long): List<Brand>

    /** status 가 null 이면 전체. 최신 등록순. */
    fun findPage(status: BrandStatus?, page: Int, size: Int): PageResult<Brand>
    fun findAllByIds(ids: Collection<Long>): List<Brand>
}
