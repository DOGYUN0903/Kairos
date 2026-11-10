package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.OrderItem
import com.dogyun.kairos.order.domain.OrderItemStatus
import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.OrderStatus
import com.dogyun.kairos.order.domain.SellerOrder
import com.dogyun.kairos.order.domain.SellerOrderStatus
import com.dogyun.kairos.order.domain.ShippingAddress
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.Predicate
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * 주문 애그리거트 저장소. 새 행은 INSERT, 기존 행은 `change(...)` 로 갱신한다(주문 → 판매자 주문 → 항목 순).
 * 조회는 orders → seller_order → order_item 세 쿼리로 조립한다. `*ForUpdate` 는 orders 행만 잠그고 나머지는 같은 조립을 쓴다.
 */
@Repository
class OrderRepositoryImpl(
    private val orderJpaRepository: OrderJpaRepository,
    private val sellerOrderJpaRepository: SellerOrderJpaRepository,
    private val orderItemJpaRepository: OrderItemJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : OrderRepository {

    override fun save(order: Order): Order {
        val orderEntity = saveOrder(order)
        val currentSellerOrders = sellerOrderJpaRepository.findAllByOrderIdInOrderByIdAsc(listOf(orderEntity.id)).associateBy { it.id }
        val currentItems = if (currentSellerOrders.isEmpty()) emptyMap()
        else orderItemJpaRepository.findAllBySellerOrderIdInOrderByIdAsc(currentSellerOrders.keys).associateBy { it.id }

        order.sellerOrders.forEach { sellerOrder ->
            val sellerOrderEntity = saveSellerOrder(orderEntity, sellerOrder, currentSellerOrders)
            sellerOrder.items.forEach { saveItem(sellerOrderEntity, it, currentItems) }
        }
        orderJpaRepository.flush()
        return requireNotNull(findById(orderEntity.id)) { "방금 저장한 주문을 다시 읽지 못했습니다: ${orderEntity.id}" }
    }

    override fun findById(id: Long): Order? = orderJpaRepository.findByIdOrNull(id)?.let { assemble(listOf(it)).single() }

    override fun findByIdForUpdate(id: Long): Order? = orderJpaRepository.findLockedById(id)?.let { assemble(listOf(it)).single() }

    override fun findByOrderNo(orderNo: String): Order? = orderJpaRepository.findByOrderNo(orderNo)?.let { assemble(listOf(it)).single() }

    override fun findByOrderNoForUpdate(orderNo: String): Order? =
        orderJpaRepository.findLockedByOrderNo(orderNo)?.let { assemble(listOf(it)).single() }

    override fun findBySellerOrderId(sellerOrderId: Long): Order? =
        sellerOrderJpaRepository.findOrderIdById(sellerOrderId)?.let(::findById)

    override fun findByOrderItemId(orderItemId: Long): Order? =
        orderItemJpaRepository.findOrderIdById(orderItemId)?.let(::findById)

    override fun findOrderIdBySellerOrderId(sellerOrderId: Long): Long? = sellerOrderJpaRepository.findOrderIdById(sellerOrderId)

    override fun findPageByMemberId(memberId: Long, page: Int, size: Int): PageResult<Order> =
        findPageOfOrders(QOrderEntity.orderEntity.memberId.eq(memberId), page, size)

    /** 한 주문에는 판매자당 판매자 주문이 하나뿐이다. 결제 전 판매자 주문은 제외하고 판매자 주문 id 내림차순으로 정렬한다. */
    override fun findPageBySellerId(sellerId: Long, status: SellerOrderStatus?, page: Int, size: Int): PageResult<Order> {
        val so = QSellerOrderEntity.sellerOrderEntity
        val where = BooleanBuilder()
            .and(so.sellerId.eq(sellerId))
            .and(so.sellerOrderStatus.ne(SellerOrderStatus.PENDING_PAYMENT))
        status?.let { where.and(so.sellerOrderStatus.eq(it)) }
        val orderIds = queryFactory.select(so.order.id).from(so)
            .where(where)
            .orderBy(so.id.desc())
            .offset(page.toLong() * size)
            .limit(size.toLong())
            .fetch()
        val total = queryFactory.select(so.count()).from(so).where(where).fetchOne() ?: 0L
        return PageResult.of(loadInOrder(orderIds), page, size, total)
    }

    override fun findPageForAdmin(status: OrderStatus?, memberId: Long?, orderNo: String?, page: Int, size: Int): PageResult<Order> {
        val o = QOrderEntity.orderEntity
        val where = BooleanBuilder()
        status?.let { where.and(o.orderStatus.eq(it)) }
        memberId?.let { where.and(o.memberId.eq(it)) }
        orderNo?.let { where.and(o.orderNo.eq(it)) }
        return findPageOfOrders(where, page, size)
    }

    private fun findPageOfOrders(where: Predicate, page: Int, size: Int): PageResult<Order> {
        val o = QOrderEntity.orderEntity
        val orderIds = queryFactory.select(o.id).from(o)
            .where(where)
            .orderBy(o.id.desc())
            .offset(page.toLong() * size)
            .limit(size.toLong())
            .fetch()
        val total = queryFactory.select(o.count()).from(o).where(where).fetchOne() ?: 0L
        return PageResult.of(loadInOrder(orderIds), page, size, total)
    }

    /** 주문 id 순서를 유지한 채 세 쿼리로 한꺼번에 조립한다. */
    private fun loadInOrder(orderIds: List<Long>): List<Order> {
        if (orderIds.isEmpty()) return emptyList()
        val byId = assemble(orderJpaRepository.findAllById(orderIds)).associateBy { it.id }
        return orderIds.mapNotNull { byId[it] }
    }

    override fun findExpiredPendingIds(now: LocalDateTime, afterId: Long, limit: Int): List<Long> {
        val o = QOrderEntity.orderEntity
        return queryFactory.select(o.id).from(o)
            .where(o.orderStatus.eq(OrderStatus.PENDING_PAYMENT), o.expiresAt.before(now), o.id.gt(afterId))
            .orderBy(o.id.asc())
            .limit(limit.toLong())
            .fetch()
    }

    override fun findSellerOrderIdsByStatus(status: SellerOrderStatus, afterId: Long, limit: Int): List<Long> {
        val so = QSellerOrderEntity.sellerOrderEntity
        return queryFactory.select(so.id).from(so)
            .where(so.sellerOrderStatus.eq(status), so.id.gt(afterId))
            .orderBy(so.id.asc())
            .limit(limit.toLong())
            .fetch()
    }

    override fun findSellerOrderIdsDeliveredBefore(threshold: LocalDateTime, afterId: Long, limit: Int): List<Long> {
        val so = QSellerOrderEntity.sellerOrderEntity
        return queryFactory.select(so.id).from(so)
            .where(so.sellerOrderStatus.eq(SellerOrderStatus.DELIVERED), so.deliveredAt.before(threshold), so.id.gt(afterId))
            .orderBy(so.id.asc())
            .limit(limit.toLong())
            .fetch()
    }

    /** 항목은 `CANCEL_REQUESTED` 로 바뀔 때 갱신되므로 `modified_at` 이 요청 시각이다. */
    override fun findCancelRequestedItems(before: LocalDateTime, afterItemId: Long, limit: Int): List<Pair<Long, Long>> {
        val i = QOrderItemEntity.orderItemEntity
        return queryFactory.select(i.sellerOrder.order.id, i.id).from(i)
            .where(i.orderItemStatus.eq(OrderItemStatus.CANCEL_REQUESTED), i.modifiedAt.before(before), i.id.gt(afterItemId))
            .orderBy(i.id.asc())
            .limit(limit.toLong())
            .fetch()
            .map { requireNotNull(it.get(0, Long::class.javaObjectType)) to requireNotNull(it.get(1, Long::class.javaObjectType)) }
    }

    override fun findCanceledIdsAmong(orderIds: Collection<Long>): Set<Long> {
        if (orderIds.isEmpty()) return emptySet()
        val o = QOrderEntity.orderEntity
        return queryFactory.select(o.id).from(o)
            .where(o.id.`in`(orderIds), o.orderStatus.eq(OrderStatus.CANCELED))
            .fetch().toSet()
    }

    private fun saveOrder(order: Order): OrderEntity {
        val id = order.id
        val entity = if (id == null) {
            val a = order.shippingAddress
            OrderEntity(
                order.orderNo, order.memberId, a.receiverName, a.phone, a.zipCode, a.address1, a.address2, a.memo,
                order.itemsAmount, order.buyerShippingFee, order.totalAmount, order.freeShippingApplied, order.expiresAt,
                order.productCouponDiscount, order.cartCouponId, order.cartCouponDiscount, order.pointsUsed,
                order.status, order.shippingFeeRecovered, order.refundedAmount, order.paidAt, order.pointsRefunded,
            )
        } else {
            requireNotNull(orderJpaRepository.findByIdOrNull(id)) { "존재하지 않는 주문입니다: $id" }
                .also { it.change(order.status, order.shippingFeeRecovered, order.refundedAmount, order.paidAt, order.pointsRefunded) }
        }
        return orderJpaRepository.saveAndFlush(entity)
    }

    private fun saveSellerOrder(order: OrderEntity, so: SellerOrder, current: Map<Long, SellerOrderEntity>): SellerOrderEntity {
        val id = so.id
        val entity = if (id == null) {
            SellerOrderEntity(
                order, so.sellerId, so.sellerName, so.commissionRateBp, so.shippingFee, so.shippingFeePayer, so.status,
                so.carrierCode, so.trackingNumber, so.shippedAt, so.deliveredAt, so.confirmedAt,
            )
        } else {
            requireNotNull(current[id]) { "이 주문의 판매자 주문이 아닙니다: $id" }
                .also { it.change(so.status, so.carrierCode, so.trackingNumber, so.shippedAt, so.deliveredAt, so.confirmedAt) }
        }
        return sellerOrderJpaRepository.saveAndFlush(entity)
    }

    private fun saveItem(sellerOrder: SellerOrderEntity, item: OrderItem, current: Map<Long, OrderItemEntity>) {
        val id = item.id
        val entity = if (id == null) {
            OrderItemEntity(
                sellerOrder, item.skuId, item.productId, item.productName, item.brandName, item.optionDisplayName,
                item.salePrice, item.extraPrice, item.quantity, item.status, item.cancelActor, item.cancelReason,
                item.refundAmount, item.shippingFeeDeducted, item.shippingFeeReturned, item.canceledAt,
                item.productCouponId, item.productCouponDiscount, item.productCouponIssuer, item.cartCouponAllocated,
                item.pointsAllocated, item.paidAmount, item.pointsRefunded,
            )
        } else {
            requireNotNull(current[id]) { "이 주문의 항목이 아닙니다: $id" }.also {
                it.change(
                    item.status, item.cancelActor, item.cancelReason, item.refundAmount, item.shippingFeeDeducted,
                    item.shippingFeeReturned, item.canceledAt, item.pointsRefunded,
                )
            }
        }
        orderItemJpaRepository.saveAndFlush(entity)
    }

    private fun assemble(orders: List<OrderEntity>): List<Order> {
        if (orders.isEmpty()) return emptyList()
        val sellerOrders = sellerOrderJpaRepository.findAllByOrderIdInOrderByIdAsc(orders.map { it.id })
        val items = if (sellerOrders.isEmpty()) emptyList()
        else orderItemJpaRepository.findAllBySellerOrderIdInOrderByIdAsc(sellerOrders.map { it.id })
        val itemsBySellerOrder = items.groupBy { it.sellerOrder.id }
        val sellerOrdersByOrder = sellerOrders.groupBy { it.order.id }
        return orders.map { toDomain(it, sellerOrdersByOrder[it.id].orEmpty(), itemsBySellerOrder) }
    }

    private fun toDomain(e: OrderEntity, sellerOrders: List<SellerOrderEntity>, items: Map<Long, List<OrderItemEntity>>) = Order.restore(
        id = e.id,
        orderNo = e.orderNo,
        memberId = e.memberId,
        status = e.orderStatus,
        shippingAddress = ShippingAddress(e.receiverName, e.receiverPhone, e.zipCode, e.address1, e.address2, e.deliveryMemo),
        itemsAmount = e.itemsAmount,
        buyerShippingFee = e.buyerShippingFee,
        totalAmount = e.totalAmount,
        freeShippingApplied = e.freeShippingApplied,
        shippingFeeRecovered = e.shippingFeeRecovered,
        refundedAmount = e.refundedAmount,
        productCouponDiscount = e.productCouponDiscount,
        cartCouponId = e.cartCouponId,
        cartCouponDiscount = e.cartCouponDiscount,
        pointsUsed = e.pointsUsed,
        pointsRefunded = e.pointsRefunded,
        expiresAt = e.expiresAt,
        paidAt = e.paidAt,
        sellerOrders = sellerOrders.map { so ->
            SellerOrder(
                so.id, so.sellerId, so.sellerName, so.commissionRateBp, so.sellerOrderStatus, so.shippingFee, so.shippingFeePayer,
                so.carrierCode, so.trackingNumber, so.shippedAt, so.deliveredAt, so.confirmedAt,
                items[so.id].orEmpty().map {
                    OrderItem(
                        it.id, it.skuId, it.productId, it.productName, it.brandName, it.optionDisplayName, it.salePrice,
                        it.extraPrice, it.quantity, it.orderItemStatus, it.cancelActor, it.cancelReason, it.refundAmount,
                        it.shippingFeeDeducted, it.shippingFeeReturned, it.canceledAt, it.productCouponId,
                        it.productCouponDiscount, it.productCouponIssuer, it.cartCouponAllocated, it.pointsAllocated,
                        it.paidAmount, it.pointsRefunded,
                    )
                },
            )
        },
        createdAt = e.createdAt,
    )
}
