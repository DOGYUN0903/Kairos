package com.dogyun.kairos.order.domain

import com.dogyun.kairos.common.domain.PageResult
import java.time.LocalDateTime

interface OrderRepository {
    fun save(order: Order): Order
    fun findById(id: Long): Order?

    /** `SELECT … FOR UPDATE`. 주문 상태를 바꾸는 트랜잭션 전용. */
    fun findByIdForUpdate(id: Long): Order?
    fun findByOrderNo(orderNo: String): Order?
    fun findByOrderNoForUpdate(orderNo: String): Order?
    fun findBySellerOrderId(sellerOrderId: Long): Order?
    fun findBySellerOrderIdForUpdate(sellerOrderId: Long): Order?
    fun findPageByMemberId(memberId: Long, page: Int, size: Int): PageResult<Order>
    fun findPageBySellerId(sellerId: Long, status: SellerOrderStatus?, page: Int, size: Int): PageResult<Order>
    fun findPageForAdmin(status: OrderStatus?, memberId: Long?, orderNo: String?, page: Int, size: Int): PageResult<Order>
    fun findExpiredPendingIds(now: LocalDateTime, limit: Int): List<Long>
    fun findSellerOrderIdsByStatus(status: SellerOrderStatus, limit: Int): List<Long>
    fun findSellerOrderIdsDeliveredBefore(threshold: LocalDateTime, limit: Int): List<Long>

    /** orderId to itemId */
    fun findCancelRequestedItems(before: LocalDateTime, limit: Int): List<Pair<Long, Long>>
    fun findCanceledOrderIdsPaidWithin(since: LocalDateTime, limit: Int): List<Long>
}
