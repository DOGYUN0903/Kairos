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
            order.createdAt ?: LocalDateTime.of(2026, 11, 10, 12, 0),
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
        rows.values.filter { o ->
            o.sellerOrders.any {
                it.sellerId == sellerId && it.status != SellerOrderStatus.PENDING_PAYMENT && (status == null || it.status == status)
            }
        }
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

    /** restore 호출 때 받은 목록(순서 그대로). */
    val restoreCalls = mutableListOf<SkuQuantity>()

    override fun restore(items: List<SkuQuantity>) {
        restoreCalls.addAll(items)
        items.forEach { stock.merge(it.skuId, it.quantity, Int::plus) }
    }

    val sales = mutableMapOf<Long, Int>()

    override fun increaseSales(items: List<ProductQuantity>) = items.forEach { sales.merge(it.productId, it.quantity, Int::plus) }
    override fun decreaseSales(items: List<ProductQuantity>) = items.forEach { sales.merge(it.productId, -it.quantity, Int::plus) }
}

data class CancelCall(val paymentKey: String, val amount: Long, val reason: String, val idempotencyKey: String)

/** 결제 상태 전이는 payment 도메인과 같은 규칙으로 흉내 낸다. 이후 Task 가 쓰는 조회·대사 메서드는 아직 비어 있다. */
class FakePaymentGateway : PaymentGateway {
    val payments = mutableMapOf<Long, PaymentInfo>()
    private var seq = 0L

    /** confirmWithPg 가 돌려줄 결과. */
    var nextConfirm: PgOutcome = PgOutcome.Unknown
    var nextCancel: PgOutcome = PgOutcome.Unknown
    val confirmCalls = mutableListOf<Triple<String, String, Long>>()
    val cancelCalls = mutableListOf<CancelCall>()
    val voidedOrderIds = mutableListOf<Long>()

    /** confirmWithPg 가 결과를 돌려주기 직전에 실행한다(PG 응답을 기다리는 사이 다른 흐름이 끼어드는 상황을 만든다). */
    var duringConfirm: (() -> Unit)? = null

    override fun prepare(orderId: Long, orderNo: String, amount: Long) {
        payments[orderId] = PaymentInfo(++seq, orderId, orderNo, null, amount, 0, "READY", null)
    }

    override fun get(orderId: Long): PaymentInfo = payments[orderId] ?: throw DomainException(ErrorCode.PAYMENT_NOT_FOUND)

    override fun start(orderId: Long, paymentKey: String): PaymentInfo {
        if (payments.values.any { it.paymentKey == paymentKey && it.orderId != orderId }) {
            throw DomainException(ErrorCode.PAYMENT_KEY_DUPLICATED)
        }
        return change(orderId, setOf("READY")) { it.copy(paymentKey = paymentKey, status = "IN_PROGRESS") }
    }

    override fun approve(orderId: Long) {
        change(orderId, setOf("IN_PROGRESS", "UNKNOWN")) { it.copy(status = "APPROVED") }
    }

    override fun fail(orderId: Long, reason: String) {
        change(orderId, setOf("READY", "IN_PROGRESS", "UNKNOWN")) { it.copy(status = "FAILED", failReason = reason) }
    }

    override fun markUnknown(orderId: Long) {
        change(orderId, setOf("IN_PROGRESS")) { it.copy(status = "UNKNOWN") }
    }

    override fun voidAfterApproval(orderId: Long) {
        change(orderId, setOf("IN_PROGRESS", "UNKNOWN")) { it.copy(status = "CANCELED", refundedAmount = it.amount) }
        voidedOrderIds += orderId
    }

    override fun recordRefund(orderId: Long, amount: Long) {
        val current = get(orderId)
        if (current.status !in setOf("APPROVED", "PARTIAL_CANCELED") || amount <= 0 || current.refundedAmount + amount > current.amount) {
            throw DomainException(ErrorCode.PAYMENT_INVALID_STATE_TRANSITION)
        }
        val refunded = current.refundedAmount + amount
        payments[orderId] = current.copy(refundedAmount = refunded, status = if (refunded == current.amount) "CANCELED" else "PARTIAL_CANCELED")
    }

    override fun findStale(inProgressBefore: LocalDateTime, limit: Int): List<PaymentInfo> = unsupported()

    override fun confirmWithPg(paymentKey: String, orderNo: String, amount: Long): PgOutcome {
        confirmCalls += Triple(paymentKey, orderNo, amount)
        duringConfirm?.invoke()
        return nextConfirm
    }

    override fun cancelWithPg(paymentKey: String, amount: Long, reason: String, idempotencyKey: String): PgOutcome {
        cancelCalls += CancelCall(paymentKey, amount, reason, idempotencyKey)
        return nextCancel
    }

    override fun findFromPg(paymentKey: String): PgOutcome = unsupported()

    private fun change(orderId: Long, allowed: Set<String>, action: (PaymentInfo) -> PaymentInfo): PaymentInfo {
        val current = get(orderId)
        if (current.status !in allowed) throw DomainException(ErrorCode.PAYMENT_INVALID_STATE_TRANSITION)
        return action(current).also { payments[orderId] = it }
    }

    private fun unsupported(): Nothing = throw UnsupportedOperationException("이후 Task 에서 채운다")
}

class FakeCartItemRemover : CartItemRemover {
    val removed = mutableListOf<Pair<Long, Set<Long>>>()

    override fun removeBySkuIds(memberId: Long, skuIds: Collection<Long>) {
        removed += memberId to skuIds.toSet()
    }
}
