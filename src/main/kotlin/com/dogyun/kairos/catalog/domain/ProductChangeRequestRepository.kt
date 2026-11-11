package com.dogyun.kairos.catalog.domain

import com.dogyun.kairos.common.domain.ListOrder
import com.dogyun.kairos.common.domain.PageResult

interface ProductChangeRequestRepository {
    fun save(changeRequest: ProductChangeRequest): ProductChangeRequest
    fun findById(id: Long): ProductChangeRequest?
    fun findPendingByProductId(productId: Long): ProductChangeRequest?
    /** 상태와 무관하게 가장 최근(id 최대) 요청. */
    fun findLatestByProductId(productId: Long): ProductChangeRequest?
    /** 주어진 상품 중 PENDING 변경 요청이 있는 상품 ID. 한 번의 쿼리로 구한다. */
    fun findPendingProductIds(productIds: Collection<Long>): Set<Long>

    /** id 기준 정렬(LATEST 내림차순, OLDEST 오름차순). status 가 null 이면 전체. */
    fun findPage(status: ChangeRequestStatus?, order: ListOrder, page: Int, size: Int): PageResult<ProductChangeRequest>
}
