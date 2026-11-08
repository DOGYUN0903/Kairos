package com.dogyun.kairos.order.domain

interface OrderRepository {
    fun save(order: Order): Order
    fun findById(id: Long): Order?

    /** CANCELLED 를 제외한 회원의 딜별 주문 수량 합. */
    fun sumActiveQuantity(memberId: Long, dealId: Long): Int
    fun findByMemberId(memberId: Long): List<Order>
}
