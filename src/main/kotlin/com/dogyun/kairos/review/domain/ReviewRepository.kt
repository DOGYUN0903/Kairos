package com.dogyun.kairos.review.domain

import com.dogyun.kairos.common.domain.PageResult

interface ReviewRepository {
    fun save(review: Review): Review

    /** 삭제된 후기는 없는 것으로 본다. */
    fun findById(id: Long): Review?

    /**
     * `SELECT … FOR UPDATE`. 수정·삭제·답글은 이 현재 읽기로 시작한다. 일반 읽기로 옛 별점을 보면
     * 동시 수정이 같은 옛 별점으로 차이를 계산해 집계가 어긋나고, 동시 삭제가 둘 다 −1 을 한다.
     */
    fun findByIdForUpdate(id: Long): Review?

    /** 삭제된 후기를 포함한다(삭제 후 재작성 금지). 최종 방어선은 `UNIQUE(order_item_id)` 다. */
    fun existsAnyByOrderItemId(orderItemId: Long): Boolean

    /** 소프트 삭제. 이번 호출이 ACTIVE → DELETED 로 바꿨으면 true. */
    fun delete(review: Review): Boolean

    /** 모두 최신순(id 내림차순), 삭제된 후기 제외. */
    fun findPageByProductId(productId: Long, page: Int, size: Int): PageResult<Review>
    fun findPageByMemberId(memberId: Long, page: Int, size: Int): PageResult<Review>
    fun findPageBySellerId(sellerId: Long, page: Int, size: Int): PageResult<Review>
}
