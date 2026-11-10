package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.OrderItem
import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.OrderStatus
import com.dogyun.kairos.order.domain.SellerOrder
import com.dogyun.kairos.order.domain.SellerOrderStatus
import java.time.LocalDateTime

class InlineTransactionRunner : TransactionRunner {
    override fun <T> run(block: () -> T): T = block()
}

/** 저장 시 id 를 채워(주문 1.., 판매자 주문 100.., 항목 1000..) DB 에서 읽은 것처럼 돌려준다. */
class FakeOrderRepository : OrderRepository {
    private val rows = LinkedHashMap<Long, Order>()
    private var orderSeq = 0L
    private var sellerOrderSeq = 100L
    private var itemSeq = 1000L

    val all: List<Order> get() = rows.values.toList()

    override fun save(order: Order): Order {
        val id = order.id ?: ++orderSeq
        val saved = Order(
            id, order.orderNo, order.memberId, order.status, order.shippingAddress, order.itemsAmount, order.buyerShippingFee,
            order.totalAmount, order.freeShippingApplied, order.shippingFeeRecovered, order.refundedAmount, order.expiresAt,
            order.paidAt, order.sellerOrders.map { so ->
                SellerOrder(
                    so.id ?: ++sellerOrderSeq, so.sellerId, so.sellerName, so.commissionRateBp, so.status, so.shippingFee,
                    so.shippingFeePayer, so.carrierCode, so.trackingNumber, so.shippedAt, so.deliveredAt, so.confirmedAt,
                    so.items.map {
                        OrderItem(
                            it.id ?: ++itemSeq, it.skuId, it.productId, it.productName, it.brandName, it.optionDisplayName,
                            it.salePrice, it.extraPrice, it.quantity, it.status, it.cancelActor, it.cancelReason,
                            it.refundAmount, it.shippingFeeDeducted, it.shippingFeeReturned, it.canceledAt,
                        )
                    },
                )
            },
            order.createdAt,
        )
        rows[id] = saved
        return saved
    }

    override fun findById(id: Long): Order? = rows[id]
    override fun findByIdForUpdate(id: Long): Order? = rows[id]
    override fun findByOrderNo(orderNo: String): Order? = rows.values.firstOrNull { it.orderNo == orderNo }
    override fun findByOrderNoForUpdate(orderNo: String): Order? = findByOrderNo(orderNo)
    override fun findBySellerOrderId(sellerOrderId: Long): Order? =
        rows.values.firstOrNull { o -> o.sellerOrders.any { it.id == sellerOrderId } }

    override fun findBySellerOrderIdForUpdate(sellerOrderId: Long): Order? = findBySellerOrderId(sellerOrderId)

    override fun findPageByMemberId(memberId: Long, page: Int, size: Int): PageResult<Order> =
        rows.values.filter { it.memberId == memberId }.sortedByDescending { it.id }.let { page(it, page, size) }

    override fun findPageBySellerId(sellerId: Long, status: SellerOrderStatus?, page: Int, size: Int): PageResult<Order> =
        rows.values.filter { o -> o.sellerOrders.any { it.sellerId == sellerId && (status == null || it.status == status) } }
            .sortedByDescending { it.id }.let { page(it, page, size) }

    override fun findPageForAdmin(status: OrderStatus?, memberId: Long?, orderNo: String?, page: Int, size: Int): PageResult<Order> =
        rows.values.filter {
            (status == null || it.status == status) && (memberId == null || it.memberId == memberId) &&
                (orderNo == null || it.orderNo == orderNo)
        }.sortedByDescending { it.id }.let { page(it, page, size) }

    override fun findExpiredPendingIds(now: LocalDateTime, limit: Int): List<Long> =
        rows.values.filter { it.status == OrderStatus.PENDING_PAYMENT && it.expiresAt.isBefore(now) }.take(limit).mapNotNull { it.id }

    override fun findSellerOrderIdsByStatus(status: SellerOrderStatus, limit: Int): List<Long> =
        rows.values.flatMap { it.sellerOrders }.filter { it.status == status }.take(limit).mapNotNull { it.id }

    override fun findSellerOrderIdsDeliveredBefore(threshold: LocalDateTime, limit: Int): List<Long> =
        rows.values.flatMap { it.sellerOrders }
            .filter { it.status == SellerOrderStatus.DELIVERED && it.deliveredAt?.isBefore(threshold) == true }
            .take(limit).mapNotNull { it.id }

    override fun findCancelRequestedItems(before: LocalDateTime, limit: Int): List<Pair<Long, Long>> = emptyList()
    override fun findCanceledOrderIdsPaidWithin(since: LocalDateTime, limit: Int): List<Long> = emptyList()

    private fun page(list: List<Order>, page: Int, size: Int) =
        PageResult.of(list.drop(page * size).take(size), page, size, list.size.toLong())
}

class FakeProductReader : ProductReader {
    val skus = mutableMapOf<Long, OrderableSkuInfo>()

    fun put(
        skuId: Long,
        sellerId: Long = 1L,
        salePrice: Long = 9_000,
        extraPrice: Long = 0,
        available: Boolean = true,
    ) {
        skus[skuId] = OrderableSkuInfo(
            skuId, 100L + skuId, "티셔츠$skuId", "브랜드", "블랙 / M", salePrice, extraPrice, sellerId, available,
        )
    }

    override fun findOrderableSkus(skuIds: Collection<Long>): Map<Long, OrderableSkuInfo> =
        skuIds.mapNotNull { skus[it] }.associateBy { it.skuId }
}

class FakeSellerReader : SellerReader {
    val sellers = mutableMapOf<Long, SellerInfo>()

    fun put(sellerId: Long, commissionRateBp: Int = 1000) {
        sellers[sellerId] = SellerInfo(sellerId, "판매자$sellerId", commissionRateBp)
    }

    override fun findSellers(sellerIds: Collection<Long>): Map<Long, SellerInfo> =
        sellerIds.mapNotNull { sellers[it] }.associateBy { it.sellerId }
}

class FakeStockManager : StockManager {
    val stock = mutableMapOf<Long, Int>()

    /** decrease 호출 때 받은 목록(순서 그대로). */
    val calls = mutableListOf<SkuQuantity>()

    override fun decrease(items: List<SkuQuantity>) {
        calls.addAll(items)
        val remaining = stock.toMutableMap()
        for (item in items) {
            val left = remaining[item.skuId] ?: 0
            if (left < item.quantity) throw DomainException(ErrorCode.STOCK_SOLD_OUT)
            remaining[item.skuId] = left - item.quantity
        }
        stock.putAll(remaining)
    }

    override fun restore(items: List<SkuQuantity>) = items.forEach { stock.merge(it.skuId, it.quantity, Int::plus) }
    override fun increaseSales(items: List<ProductQuantity>) = Unit
    override fun decreaseSales(items: List<ProductQuantity>) = Unit
}

class FakePaymentGateway : PaymentGateway {
    val payments = mutableMapOf<Long, PaymentInfo>()
    private var seq = 0L

    override fun prepare(orderId: Long, orderNo: String, amount: Long) {
        payments[orderId] = PaymentInfo(++seq, orderId, orderNo, null, amount, 0, "READY", null)
    }

    override fun get(orderId: Long): PaymentInfo = payments.getValue(orderId)

    override fun start(orderId: Long, paymentKey: String): PaymentInfo = unsupported()
    override fun approve(orderId: Long) = unsupported()
    override fun fail(orderId: Long, reason: String) = unsupported()
    override fun markUnknown(orderId: Long) = unsupported()
    override fun voidAfterApproval(orderId: Long) = unsupported()
    override fun recordRefund(orderId: Long, amount: Long) = unsupported()
    override fun findStale(inProgressBefore: LocalDateTime, limit: Int): List<PaymentInfo> = unsupported()
    override fun confirmWithPg(paymentKey: String, orderNo: String, amount: Long): PgOutcome = unsupported()
    override fun cancelWithPg(paymentKey: String, amount: Long, reason: String, idempotencyKey: String): PgOutcome = unsupported()
    override fun findFromPg(paymentKey: String): PgOutcome = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("이후 Task 에서 채운다")
}

class FakeCartItemRemover : CartItemRemover {
    val removed = mutableListOf<Pair<Long, Set<Long>>>()

    override fun removeBySkuIds(memberId: Long, skuIds: Collection<Long>) {
        removed += memberId to skuIds.toSet()
    }
}
