package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.OrderItem
import com.dogyun.kairos.order.domain.OrderItemStatus
import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.OrderStatus
import com.dogyun.kairos.order.domain.PointUsePolicy
import com.dogyun.kairos.order.domain.ReturnRequest
import com.dogyun.kairos.order.domain.ReturnStatus
import com.dogyun.kairos.order.domain.SellerOrder
import com.dogyun.kairos.order.domain.SellerOrderStatus
import java.time.LocalDateTime

/** 저장 시 id 를 채워(주문 1.., 판매자 주문 100.., 항목 1000..) DB 에서 읽은 것처럼 돌려준다. */
class FakeOrderRepository : OrderRepository {
    private val rows = LinkedHashMap<Long, Order>()
    private var orderSeq = 0L
    private var sellerOrderSeq = 100L
    private var itemSeq = 1000L
    private var returnSeq = 0L

    val all: List<Order> get() = rows.values.toList()

    override fun save(order: Order): Order {
        val id = order.id ?: ++orderSeq
        val saved = Order(
            id, order.orderNo, order.memberId, order.status, order.shippingAddress, order.itemsAmount, order.buyerShippingFee,
            order.totalAmount, order.freeShippingApplied, order.shippingFeeRecovered, order.refundedAmount,
            order.productCouponDiscount, order.cartCouponId, order.cartCouponDiscount, order.pointsUsed, order.pointsRefunded,
            order.expiresAt,
            order.paidAt, order.sellerOrders.map { so ->
                SellerOrder(
                    so.id ?: ++sellerOrderSeq, so.sellerId, so.sellerName, so.commissionRateBp, so.status, so.shippingFee,
                    so.shippingFeePayer, so.carrierCode, so.trackingNumber, so.shippedAt, so.deliveredAt, so.confirmedAt,
                    so.items.map {
                        OrderItem(
                            it.id ?: ++itemSeq, it.skuId, it.productId, it.productName, it.brandName, it.optionDisplayName,
                            it.salePrice, it.extraPrice, it.quantity, it.status, it.cancelActor, it.cancelReason,
                            it.refundAmount, it.shippingFeeDeducted, it.shippingFeeReturned, it.canceledAt, it.productCouponId,
                            it.productCouponDiscount, it.productCouponIssuer, it.cartCouponAllocated, it.pointsAllocated,
                            it.paidAmount, it.pointsRefunded,
                            it.returnRequest?.let { r -> if (r.id != null) r else saveReturn(r) },
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

    override fun findByOrderItemId(orderItemId: Long): Order? =
        rows.values.firstOrNull { o -> o.sellerOrders.any { so -> so.items.any { it.id == orderItemId } } }

    override fun findOrderIdBySellerOrderId(sellerOrderId: Long): Long? = findBySellerOrderId(sellerOrderId)?.id

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

    override fun findExpiredPendingIds(now: LocalDateTime, afterId: Long, limit: Int): List<Long> =
        rows.values.filter { it.status == OrderStatus.PENDING_PAYMENT && it.expiresAt.isBefore(now) }
            .mapNotNull { it.id }.filter { it > afterId }.sorted().take(limit)

    override fun findSellerOrderIdsByStatus(status: SellerOrderStatus, afterId: Long, limit: Int): List<Long> =
        rows.values.flatMap { it.sellerOrders }.filter { it.status == status }
            .mapNotNull { it.id }.filter { it > afterId }.sorted().take(limit)

    override fun findSellerOrderIdsConfirmedBetween(from: LocalDateTime, to: LocalDateTime, afterId: Long, limit: Int): List<Long> =
        rows.values.flatMap { it.sellerOrders }
            .filter { it.status == SellerOrderStatus.CONFIRMED && it.confirmedAt?.let { c -> !c.isBefore(from) && c.isBefore(to) } == true }
            .mapNotNull { it.id }.filter { it > afterId }.sorted().take(limit)

    override fun findSellerOrderIdsDeliveredBefore(threshold: LocalDateTime, afterId: Long, limit: Int): List<Long> =
        rows.values.flatMap { it.sellerOrders }
            .filter { it.status == SellerOrderStatus.DELIVERED && it.deliveredAt?.isBefore(threshold) == true }
            .mapNotNull { it.id }.filter { it > afterId }.sorted().take(limit)

    override fun findItemsInStatusBefore(
        statuses: Set<OrderItemStatus>,
        before: LocalDateTime,
        afterItemId: Long,
        limit: Int,
    ): List<Pair<Long, Long>> =
        rows.values.flatMap { o -> o.allItems.filter { it.status in statuses }.map { requireNotNull(o.id) to requireNotNull(it.id) } }
            .filter { it.second > afterItemId }.sortedBy { it.second }.take(limit)

    override fun findOrderIdByReturnId(returnId: Long): Long? =
        rows.values.firstOrNull { o -> o.allItems.any { it.returnRequest?.id == returnId } }?.id

    override fun findReturnPage(memberId: Long?, sellerId: Long?, status: ReturnStatus?, page: Int, size: Int): PageResult<ReturnRequest> {
        val list = rows.values.flatMap { o -> o.allItems.mapNotNull { it.returnRequest } }
            .filter { (memberId == null || it.memberId == memberId) && (sellerId == null || it.sellerId == sellerId) && (status == null || it.status == status) }
            .sortedWith(compareByDescending<ReturnRequest> { it.requestedAt }.thenByDescending { it.id })
        return PageResult.of(list.drop(page * size).take(size), page, size, list.size.toLong())
    }

    override fun findReturnIdsRequestedBefore(threshold: LocalDateTime, afterId: Long, limit: Int): List<Pair<Long, Long>> =
        rows.values.flatMap { o ->
            o.allItems.mapNotNull { it.returnRequest }
                .filter { it.status == ReturnStatus.REQUESTED && it.requestedAt.isBefore(threshold) }
                .map { requireNotNull(o.id) to requireNotNull(it.id) }
        }.filter { it.second > afterId }.sortedBy { it.second }.take(limit)

    override fun findCanceledIdsAmong(orderIds: Collection<Long>): Set<Long> =
        rows.values.filter { it.id in orderIds && it.status == OrderStatus.CANCELED }.mapNotNull { it.id }.toSet()

    /** 새 반품에 id 를 붙인다. 같은 인스턴스를 도메인이 계속 들고 있으므로 필드를 복사한 새 객체를 돌려준다. */
    private fun saveReturn(r: ReturnRequest) = ReturnRequest(
        ++returnSeq, r.orderId, r.sellerOrderId, r.orderItemId, r.memberId, r.sellerId, r.reason, r.detail, r.status,
        r.returnFee, r.outboundFeeRecovered, r.shippingFeeRefund, r.sellerBorneFee, r.refundAmount, r.pointsRefund,
        r.rejectReason, r.requestedAt, r.decidedAt, r.decidedBy, r.receivedAt, r.completedAt,
    )

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
        thumbnailUrl: String = "https://img.test/sku$skuId.jpg",
    ) {
        skus[skuId] = OrderableSkuInfo(
            skuId, 100L + skuId, "티셔츠$skuId", "브랜드", "블랙 / M", salePrice, extraPrice, sellerId, thumbnailUrl, available,
        )
    }

    override fun findOrderableSkus(skuIds: Collection<Long>): Map<Long, OrderableSkuInfo> =
        skuIds.mapNotNull { skus[it] }.associateBy { it.skuId }

    /** 상품 ID → 대표 이미지. 여기 없는 상품(삭제 등)은 결과에서 빠진다. */
    val thumbnails = mutableMapOf<Long, String>()
    var thumbnailCalls = 0

    override fun findThumbnailUrls(productIds: Collection<Long>): Map<Long, String> {
        thumbnailCalls++
        return productIds.mapNotNull { id -> thumbnails[id]?.let { id to it } }.toMap()
    }
}

class FakeSellerReader : SellerReader {
    val sellers = mutableMapOf<Long, SellerInfo>()

    fun put(sellerId: Long, commissionRateBp: Int = 1000) {
        sellers[sellerId] = SellerInfo(sellerId, "판매자$sellerId", commissionRateBp)
    }

    override fun findSellers(sellerIds: Collection<Long>): Map<Long, SellerInfo> =
        sellerIds.mapNotNull { sellers[it] }.associateBy { it.sellerId }
}

/** [events] 를 [FakeBenefitReverter] 와 공유하면 호출 순서("restore")를 확인할 수 있다. */
class FakeStockManager(val events: MutableList<String> = mutableListOf()) : StockManager {
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
        events += "restore"
        items.forEach { stock.merge(it.skuId, it.quantity, Int::plus) }
    }

    val sales = mutableMapOf<Long, Int>()

    override fun increaseSales(items: List<ProductQuantity>) = items.forEach { sales.merge(it.productId, it.quantity, Int::plus) }
    override fun decreaseSales(items: List<ProductQuantity>) = items.forEach { sales.merge(it.productId, -it.quantity, Int::plus) }
}

data class CancelCall(val paymentKey: String, val amount: Long, val reason: String, val idempotencyKey: String)

/** 결제 상태 전이는 payment 도메인과 같은 규칙으로 흉내 낸다. */
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

    override fun approveWithoutPg(orderId: Long) {
        val current = get(orderId)
        if (current.amount != 0L) throw DomainException(ErrorCode.PAYMENT_INVALID_STATE_TRANSITION)
        change(orderId, setOf("READY")) { it.copy(status = "APPROVED") }
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

    /** 시각은 따지지 않는다(IN_PROGRESS 와 UNKNOWN 전부). */
    override fun findStale(inProgressBefore: LocalDateTime, limit: Int): List<PaymentInfo> =
        payments.values.filter { it.status == "IN_PROGRESS" || it.status == "UNKNOWN" }.take(limit)

    override fun findVoidCandidates(afterPaymentId: Long, limit: Int): List<PaymentInfo> =
        payments.values.filter { it.paymentKey != null && (it.status == "FAILED" || it.status == "UNKNOWN") && it.paymentId > afterPaymentId }
            .sortedBy { it.paymentId }.take(limit)

    override fun markVoided(orderId: Long) {
        change(orderId, setOf("FAILED", "UNKNOWN")) { it.copy(status = "CANCELED") }
    }

    override fun confirmWithPg(paymentKey: String, orderNo: String, amount: Long): PgOutcome {
        confirmCalls += Triple(paymentKey, orderNo, amount)
        duringConfirm?.invoke()
        return nextConfirm
    }

    override fun cancelWithPg(paymentKey: String, amount: Long, reason: String, idempotencyKey: String): PgOutcome {
        cancelCalls += CancelCall(paymentKey, amount, reason, idempotencyKey)
        return nextCancel
    }

    /** findFromPg 가 paymentKey 별로 돌려줄 PG 쪽 상태. 없으면 NOT_FOUND. */
    val pgPayments = mutableMapOf<String, PgOutcome>()

    override fun findFromPg(paymentKey: String): PgOutcome =
        pgPayments[paymentKey] ?: PgOutcome.Failed("NOT_FOUND", "존재하지 않는 결제입니다.")

    private fun change(orderId: Long, allowed: Set<String>, action: (PaymentInfo) -> PaymentInfo): PaymentInfo {
        val current = get(orderId)
        if (current.status !in allowed) throw DomainException(ErrorCode.PAYMENT_INVALID_STATE_TRANSITION)
        return action(current).also { payments[orderId] = it }
    }
}

class FakeCartItemRemover : CartItemRemover {
    val removed = mutableListOf<Pair<Long, Set<Long>>>()

    override fun removeBySkuIds(memberId: Long, skuIds: Collection<Long>) {
        removed += memberId to skuIds.toSet()
    }
}

/** 송장 번호별 조회 결과. 지정하지 않은 번호는 배송 중이다. */
class FakeDeliveryTracker : DeliveryTracker {
    val results = mutableMapOf<String, TrackingResult>()
    val calls = mutableListOf<Pair<String, String>>()

    override fun track(carrierCode: String, trackingNumber: String): TrackingResult {
        calls += carrierCode to trackingNumber
        return results[trackingNumber] ?: TrackingResult.IN_TRANSIT
    }
}

/** 쿠폰 조건과 포인트 잔액을 메모리에 두고 reserve·commit 호출을 기록한다. */
class FakeBenefitApplier : BenefitApplier {
    val coupons = mutableMapOf<Long, ReservedCoupon>()
    val used = mutableSetOf<Long>()
    var balance: Long = 0
    var policy = PointUsePolicy(minUsableBalance = 1_000, useUnit = 10, maxUseRateBp = 3_000)

    /** reserve 로 USED 가 된 쿠폰 ID 묶음(호출 순서대로). */
    val reserved = mutableListOf<Set<Long>>()

    data class Commit(val memberId: Long, val orderId: Long, val couponIds: Set<Long>, val pointsToUse: Long)

    val committed = mutableListOf<Commit>()

    /** 호출 순서 기록: "reserve", "commit". */
    val calls = mutableListOf<String>()

    fun product(couponId: Long, issuer: String = "PLATFORM", target: String = "ALL_PRODUCTS", discountType: String = "FIXED",
                value: Long = 2_000, max: Long? = null, min: Long? = null, sellerId: Long? = null, productId: Long? = null) {
        coupons[couponId] = ReservedCoupon(couponId, couponId, "PRODUCT", issuer, target, productId, sellerId, discountType, value, max, min)
    }

    fun cart(couponId: Long, discountType: String = "RATE", value: Long = 1_000, max: Long? = 5_000, min: Long? = null) {
        coupons[couponId] = ReservedCoupon(couponId, couponId, "CART", "PLATFORM", "NONE", null, null, discountType, value, max, min)
    }

    /** 견적의 "내 쿠폰" 목록. [coupons] 와 별개로 테스트가 채운다(비어 있으면 목록도 비어 있다). */
    val usable = mutableListOf<UsableCoupon>()

    fun hold(couponId: Long, name: String = "쿠폰$couponId", expiresAt: LocalDateTime = LocalDateTime.of(2026, 12, 31, 0, 0)) {
        usable += UsableCoupon(coupons.getValue(couponId), name, expiresAt)
    }

    override fun listUsable(memberId: Long): List<UsableCoupon> = usable.toList()

    private fun lookup(couponIds: Collection<Long>): Map<Long, ReservedCoupon> = couponIds.associateWith {
        coupons[it] ?: throw DomainException(ErrorCode.COUPON_NOT_FOUND)
    }

    override fun quote(memberId: Long, couponIds: Collection<Long>): BenefitReservation {
        val found = lookup(couponIds)
        if (couponIds.any { it in used }) throw DomainException(ErrorCode.COUPON_NOT_AVAILABLE)
        return BenefitReservation(found, balance, policy)
    }

    override fun reserve(memberId: Long, couponIds: Collection<Long>): BenefitReservation {
        calls += "reserve"
        val found = lookup(couponIds)
        if (couponIds.any { it in used }) throw DomainException(ErrorCode.COUPON_NOT_AVAILABLE)
        used += couponIds
        reserved += couponIds.toSet()
        return BenefitReservation(found, balance, policy)
    }

    override fun commit(memberId: Long, orderId: Long, couponIds: Collection<Long>, pointsToUse: Long) {
        calls += "commit"
        if (balance < pointsToUse) throw DomainException(ErrorCode.POINT_INSUFFICIENT)
        balance -= pointsToUse
        committed += Commit(memberId, orderId, couponIds.toSet(), pointsToUse)
    }
}

/** revertAll·revertItem 호출을 기록한다. [events] 를 [FakeStockManager] 와 공유하면 호출 순서를 확인할 수 있다. */
class FakeBenefitReverter(val events: MutableList<String> = mutableListOf()) : BenefitReverter {
    data class RevertAll(val orderId: Long, val memberId: Long, val couponIds: Set<Long>, val pointsUsed: Long)
    data class RevertItem(
        val orderId: Long, val memberId: Long, val orderItemId: Long, val pointsRefund: Long, val productCouponId: Long?, val cartCouponId: Long?,
    )

    val revertAllCalls = mutableListOf<RevertAll>()
    val revertItemCalls = mutableListOf<RevertItem>()

    override fun revertAll(orderId: Long, memberId: Long, couponIds: Collection<Long>, pointsUsed: Long) {
        revertAllCalls += RevertAll(orderId, memberId, couponIds.toSet(), pointsUsed)
        events += "revertAll"
    }

    override fun revertItem(orderId: Long, memberId: Long, orderItemId: Long, pointsRefund: Long, productCouponId: Long?, cartCouponId: Long?) {
        revertItemCalls += RevertItem(orderId, memberId, orderItemId, pointsRefund, productCouponId, cartCouponId)
        events += "revertItem"
    }
}

/** earnForPurchase 호출을 기록한다. 적립 값은 1%(버림)로 흉내 낸다. */
class FakePointEarner : PointEarner {
    data class Earn(val memberId: Long, val sellerOrderId: Long, val paidAmount: Long)

    val calls = mutableListOf<Earn>()

    override fun earnForPurchase(memberId: Long, sellerOrderId: Long, paidAmount: Long): Long {
        calls += Earn(memberId, sellerOrderId, paidAmount)
        return paidAmount / 100
    }
}
