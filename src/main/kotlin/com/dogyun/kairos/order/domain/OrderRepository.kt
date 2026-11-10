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

    /** 주문 항목이 속한 주문(일반 읽기, 후기 작성 자격 조회용). */
    fun findByOrderItemId(orderItemId: Long): Order?

    /**
     * 판매자 주문이 속한 주문 id. 주문 행 락을 잡는 트랜잭션 **밖에서** 부른 뒤 `findByIdForUpdate` 로 잠근다.
     * 트랜잭션 안에서 먼저 일반 조회를 하면 REPEATABLE READ 스냅샷이 락보다 먼저 만들어져, 락을 잡은 뒤에도
     * 그 전 상태(방금 커밋된 항목 취소 요청 등)를 읽는다. 판매자 주문의 주문은 바뀌지 않으므로 락 밖에서 찾아도 안전하다.
     */
    fun findOrderIdBySellerOrderId(sellerOrderId: Long): Long?
    fun findPageByMemberId(memberId: Long, page: Int, size: Int): PageResult<Order>
    /** 결제 전(PENDING_PAYMENT) 판매자 주문은 제외한다. 돌려주는 주문에는 다른 판매자의 판매자 주문도 담겨 있다. */
    fun findPageBySellerId(sellerId: Long, status: SellerOrderStatus?, page: Int, size: Int): PageResult<Order>
    fun findPageForAdmin(status: OrderStatus?, memberId: Long?, orderNo: String?, page: Int, size: Int): PageResult<Order>
    /** 정리 작업용 조회는 모두 id 오름차순 keyset(`id > afterId`)이다. 처리하지 못해 집합에 남는 항목이 뒤의 항목을 막지 않게 한다. */
    fun findExpiredPendingIds(now: LocalDateTime, afterId: Long, limit: Int): List<Long>
    fun findSellerOrderIdsByStatus(status: SellerOrderStatus, afterId: Long, limit: Int): List<Long>
    fun findSellerOrderIdsDeliveredBefore(threshold: LocalDateTime, afterId: Long, limit: Int): List<Long>

    /** orderId to itemId. 항목 id 오름차순, [afterItemId] 보다 큰 것부터. */
    fun findCancelRequestedItems(before: LocalDateTime, afterItemId: Long, limit: Int): List<Pair<Long, Long>>

    /** [orderIds] 중 `CANCELED` 인 주문 id. */
    fun findCanceledIdsAmong(orderIds: Collection<Long>): Set<Long>
}
