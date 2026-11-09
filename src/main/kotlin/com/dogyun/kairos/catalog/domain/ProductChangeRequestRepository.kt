package com.dogyun.kairos.catalog.domain

import com.dogyun.kairos.common.domain.PageResult

interface ProductChangeRequestRepository {
    fun save(changeRequest: ProductChangeRequest): ProductChangeRequest
    fun findById(id: Long): ProductChangeRequest?
    fun findPendingByProductId(productId: Long): ProductChangeRequest?
    /** 최신순. status 가 null 이면 전체. */
    fun findPage(status: ChangeRequestStatus?, page: Int, size: Int): PageResult<ProductChangeRequest>
}
