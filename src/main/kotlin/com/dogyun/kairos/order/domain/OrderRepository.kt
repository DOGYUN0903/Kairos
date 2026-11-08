package com.dogyun.kairos.order.domain

import com.dogyun.kairos.common.domain.PageResult

interface OrderRepository {
    fun save(order: Order): Order
    fun findById(id: Long): Order?

    /** CANCELLED 를 제외한 회원의 딜별 주문 수량 합. */
    fun sumActiveQuantity(memberId: Long, dealId: Long): Int
    /** 회원의 주문을 최신순(id 내림차순)으로 페이징 조회한다. */
    fun findPageByMemberId(memberId: Long, page: Int, size: Int): PageResult<Order>
}
