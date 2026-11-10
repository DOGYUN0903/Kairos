package com.dogyun.kairos.order.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.Duration
import java.time.LocalDateTime

class Order(
    val id: Long?,
    val orderNo: String,
    val memberId: Long,
    status: OrderStatus,
    val shippingAddress: ShippingAddress,
    val itemsAmount: Long,
    val buyerShippingFee: Long,
    val totalAmount: Long,
    val freeShippingApplied: Boolean,
    shippingFeeRecovered: Long,
    refundedAmount: Long,
    val productCouponDiscount: Long,
    val cartCouponId: Long?,
    val cartCouponDiscount: Long,
    val pointsUsed: Long,
    pointsRefunded: Long,
    val expiresAt: LocalDateTime,
    paidAt: LocalDateTime?,
    val sellerOrders: List<SellerOrder>,
    val createdAt: LocalDateTime?,
) {
    var status: OrderStatus = status
        private set

    /** 무료배송이 깨질 때 구매자에게서 회수한(아직 돌려주지 않은) 기본 배송비 합계. */
    var shippingFeeRecovered: Long = shippingFeeRecovered
        private set
    var refundedAmount: Long = refundedAmount
        private set
    var paidAt: LocalDateTime? = paidAt
        private set
    var pointsRefunded: Long = pointsRefunded
        private set

    val allItems: List<OrderItem> get() = sellerOrders.flatMap { it.items }
    val remainingItemsAmount: Long get() = sellerOrders.sumOf { it.remainingItemsAmount }
    /** 아직 취소되지 않은(ORDERED) 항목이 실제로 낸 금액 합계. 배송비는 포함하지 않는다. */
    val remainingPaidAmount: Long get() = allItems.filter { it.status.isRemaining }.sumOf { it.paidAmount }
    val allItemsCanceled: Boolean get() = allItems.all { it.status == OrderItemStatus.CANCELED }
    val allItemsClosed: Boolean get() = allItems.all { it.status == OrderItemStatus.CANCELED || it.status == OrderItemStatus.RETURNED }

    /** 발송 구간 배송비를 구매자에게서 회수한 합계(취소 차감 + 환불 중·완료 반품). 이중 회수를 막는 기준이다. */
    val outboundFeeRecoveredEver: Long
        get() = allItems.sumOf { it.shippingFeeDeducted } + allItems.mapNotNull { it.returnRequest }
            .filter { it.status == ReturnStatus.REFUNDING || it.status == ReturnStatus.COMPLETED }
            .sumOf { it.outboundFeeRecovered }
    val remainingBuyerShippingFee: Long
        get() = sellerOrders
            .filter { it.status != SellerOrderStatus.CANCELED && it.shippingFeePayer == ShippingFeePayer.BUYER }
            .sumOf { it.shippingFee - it.buyerShippingFeeRefunded }

    /** SKU별 수량 합계, SKU ID 오름차순(재고 복구 순서). */
    val skuQuantities: List<Pair<Long, Int>>
        get() = allItems.groupBy { it.skuId }.map { (sku, items) -> sku to items.sumOf { it.quantity } }.sortedBy { it.first }

    /** 이 주문이 쓴 쿠폰(상품 쿠폰 + 장바구니 쿠폰) ID. */
    fun usedCouponIds(): List<Long> = allItems.mapNotNull { it.productCouponId } + listOfNotNull(cartCouponId)

    fun findItem(itemId: Long): OrderItem =
        allItems.firstOrNull { it.id == itemId } ?: throw DomainException(ErrorCode.ORDER_ITEM_NOT_FOUND)

    fun findSellerOrder(id: Long): SellerOrder =
        sellerOrders.firstOrNull { it.id == id } ?: throw DomainException(ErrorCode.SELLER_ORDER_NOT_FOUND)

    fun findReturn(returnId: Long): Pair<SellerOrder, OrderItem> {
        for (so in sellerOrders) for (item in so.items) if (item.returnRequest?.id == returnId) return so to item
        throw DomainException(ErrorCode.RETURN_NOT_FOUND)
    }

    fun requestReturn(itemId: Long, reason: ReturnReason, detail: String?, window: Duration, now: LocalDateTime): ReturnRequest {
        val item = findItem(itemId)
        val sellerOrder = sellerOrderOf(itemId)
        if (item.status.isOpenReturn) throw DomainException(ErrorCode.RETURN_ALREADY_REQUESTED)
        if (status != OrderStatus.PAID || sellerOrder.status != SellerOrderStatus.DELIVERED || item.status != OrderItemStatus.ORDERED) {
            throw DomainException(ErrorCode.RETURN_NOT_ELIGIBLE)
        }
        val deliveredAt = checkNotNull(sellerOrder.deliveredAt) { "배송 완료 시각이 없는 DELIVERED 판매자 주문" }
        if (deliveredAt.plus(window).isBefore(now)) throw DomainException(ErrorCode.RETURN_WINDOW_EXPIRED)
        val existing = item.returnRequest
        return when {
            existing == null -> ReturnRequest.request(
                checkNotNull(id) { "저장되지 않은 주문" }, checkNotNull(sellerOrder.id) { "저장되지 않은 판매자 주문" },
                itemId, memberId, sellerOrder.sellerId, reason, detail, now,
            ).also { item.requestReturn(it) }
            existing.status == ReturnStatus.WITHDRAWN -> existing.also { item.reopenReturn(reason, detail, now) }
            else -> throw DomainException(ErrorCode.RETURN_ALREADY_REQUESTED)
        }
    }

    fun approveReturn(returnId: Long, by: DecidedBy, now: LocalDateTime) {
        val (sellerOrder, item) = findReturn(returnId)
        if (item.returnRequest?.status == ReturnStatus.REJECTED && sellerOrder.status == SellerOrderStatus.CONFIRMED) {
            throw DomainException(ErrorCode.RETURN_NOT_ELIGIBLE)
        }
        item.approveReturn(by, now)
    }

    fun rejectReturn(returnId: Long, reason: String, now: LocalDateTime) = findReturn(returnId).second.rejectReturn(reason, now)

    fun withdrawReturn(returnId: Long) = findReturn(returnId).second.withdrawReturn()

    fun cancelReturnByAdmin(returnId: Long) = findReturn(returnId).second.cancelReturnByAdmin()

    fun startReturnRefund(returnId: Long, plan: ReturnRefundPlan, now: LocalDateTime) {
        if (status != OrderStatus.PAID) throw DomainException(ErrorCode.RETURN_INVALID_STATE_TRANSITION)
        findReturn(returnId).second.startReturnRefund(plan, now)
    }

    fun revertReturnRefund(returnId: Long) = findReturn(returnId).second.revertReturnRefund()

    fun markPaid(now: LocalDateTime) {
        transit(setOf(OrderStatus.PENDING_PAYMENT), OrderStatus.PAID)
        paidAt = now
        sellerOrders.forEach { it.markPaid() }
    }

    fun expire() = closeUnpaid()

    fun cancelForPaymentFailure() = closeUnpaid()

    private fun closeUnpaid() {
        transit(setOf(OrderStatus.PENDING_PAYMENT), OrderStatus.CANCELED)
        sellerOrders.forEach { so ->
            so.items.forEach { it.cancelBySystem() }
            so.cancel()
        }
    }

    fun planCancel(itemId: Long, actor: CancelActor, policy: ShippingPolicy): RefundPlan =
        RefundCalculator.plan(this, itemId, actor, policy)

    fun requestCancel(itemId: Long, actor: CancelActor, reason: String?, plan: RefundPlan) {
        // 계획을 만들 때와 같은 검증(주문·항목·판매자 주문 상태)을 다시 거친다.
        findItem(itemId)
        if (status != OrderStatus.PAID) throw DomainException(ErrorCode.ORDER_INVALID_STATE_TRANSITION)
        if (sellerOrderOf(itemId).status != SellerOrderStatus.PAID) throw DomainException(ErrorCode.ORDER_ITEM_NOT_CANCELABLE)
        findItem(itemId).requestCancel(actor, reason, plan)
    }

    fun confirmCancel(itemId: Long, plan: RefundPlan, now: LocalDateTime) {
        if (status != OrderStatus.PAID) throw DomainException(ErrorCode.ORDER_INVALID_STATE_TRANSITION)
        val item = findItem(itemId)
        if (item.status != OrderItemStatus.CANCEL_REQUESTED) throw DomainException(ErrorCode.ORDER_ITEM_NOT_CANCELABLE)
        if (plan != item.toRefundPlan()) throw DomainException(ErrorCode.ORDER_INVALID)
        item.confirmCancel(now, plan.pointsRefund)
        refundedAmount += plan.refundAmount
        pointsRefunded += plan.pointsRefund
        shippingFeeRecovered += plan.shippingFeeDeducted - plan.shippingFeeReturned
        val sellerOrder = sellerOrderOf(itemId)
        if (sellerOrder.items.all { it.status == OrderItemStatus.CANCELED }) sellerOrder.cancel()
        settle()
    }

    fun revertCancel(itemId: Long) = findItem(itemId).revertCancelRequest()

    fun ship(sellerOrderId: Long, carrierCode: String, trackingNumber: String, now: LocalDateTime) =
        findSellerOrder(sellerOrderId).ship(carrierCode, trackingNumber, now)

    fun deliver(sellerOrderId: Long, now: LocalDateTime) = findSellerOrder(sellerOrderId).deliver(now)

    fun confirmPurchase(sellerOrderId: Long, now: LocalDateTime) {
        findSellerOrder(sellerOrderId).confirm(now)
        settle()
    }

    private fun sellerOrderOf(itemId: Long) = sellerOrders.first { so -> so.items.any { it.id == itemId } }

    /** 모든 판매자 주문이 끝났으면(확정·취소) 주문을 닫는다. 전부 취소면 CANCELED, 하나라도 확정이면 COMPLETED. */
    private fun settle() {
        if (status != OrderStatus.PAID) return
        val ended = sellerOrders.all { it.status == SellerOrderStatus.CONFIRMED || it.status == SellerOrderStatus.CANCELED }
        if (!ended) return
        status = if (sellerOrders.any { it.status == SellerOrderStatus.CONFIRMED }) OrderStatus.COMPLETED else OrderStatus.CANCELED
    }

    private fun transit(from: Set<OrderStatus>, to: OrderStatus) {
        if (status !in from) throw DomainException(ErrorCode.ORDER_INVALID_STATE_TRANSITION)
        status = to
    }

    companion object {
        fun create(
            orderNo: String,
            memberId: Long,
            lines: List<OrderLine>,
            allocation: DiscountAllocation,
            address: ShippingAddress,
            policy: ShippingPolicy,
            now: LocalDateTime,
            paymentTtl: Duration,
        ): Order {
            if (lines.isEmpty()) throw DomainException(ErrorCode.ORDER_INVALID)
            if (lines.any { it.quantity <= 0 || it.salePrice <= 0 || it.extraPrice < 0 }) {
                throw DomainException(ErrorCode.ORDER_INVALID)
            }
            if (lines.map { it.skuId }.toSet().size != lines.size) throw DomainException(ErrorCode.ORDER_INVALID)

            if (allocation.lines.keys != lines.map { it.skuId }.toSet()) throw DomainException(ErrorCode.ORDER_INVALID)

            val itemsAmount = lines.sumOf { (it.salePrice + it.extraPrice) * it.quantity }
            val free = itemsAmount >= policy.freeShippingThreshold
            val payer = if (free) ShippingFeePayer.PLATFORM else ShippingFeePayer.BUYER

            val sellerOrders = lines.groupBy { it.sellerId }.toSortedMap().map { (sellerId, sellerLines) ->
                SellerOrder(
                    id = null,
                    sellerId = sellerId,
                    sellerName = sellerLines.first().sellerName,
                    commissionRateBp = sellerLines.first().commissionRateBp,
                    status = SellerOrderStatus.PENDING_PAYMENT,
                    shippingFee = policy.baseShippingFee,
                    shippingFeePayer = payer,
                    carrierCode = null,
                    trackingNumber = null,
                    shippedAt = null,
                    deliveredAt = null,
                    confirmedAt = null,
                    items = sellerLines.map {
                        val a = allocation.lines.getValue(it.skuId)
                        val lineAmount = (it.salePrice + it.extraPrice) * it.quantity
                        val paid = lineAmount - a.productCouponDiscount - a.cartCouponAllocated - a.pointsAllocated
                        if (paid < 0) throw DomainException(ErrorCode.ORDER_INVALID)
                        OrderItem(
                            id = null, skuId = it.skuId, productId = it.productId, productName = it.productName,
                            brandName = it.brandName, optionDisplayName = it.optionDisplayName, salePrice = it.salePrice,
                            extraPrice = it.extraPrice, quantity = it.quantity, status = OrderItemStatus.ORDERED,
                            cancelActor = null, cancelReason = null, refundAmount = 0, shippingFeeDeducted = 0,
                            shippingFeeReturned = 0, canceledAt = null,
                            productCouponId = a.productCouponId, productCouponDiscount = a.productCouponDiscount,
                            productCouponIssuer = a.productCouponIssuer, cartCouponAllocated = a.cartCouponAllocated,
                            pointsAllocated = a.pointsAllocated, paidAmount = paid, pointsRefunded = 0, returnRequest = null,
                        )
                    },
                )
            }
            val paidItems = sellerOrders.sumOf { so -> so.items.sumOf { it.paidAmount } }
            val buyerShippingFee = sellerOrders.filter { it.shippingFeePayer == ShippingFeePayer.BUYER }.sumOf { it.shippingFee }
            return Order(
                id = null, orderNo = orderNo, memberId = memberId, status = OrderStatus.PENDING_PAYMENT,
                shippingAddress = address, itemsAmount = itemsAmount, buyerShippingFee = buyerShippingFee,
                totalAmount = paidItems + buyerShippingFee, freeShippingApplied = free, shippingFeeRecovered = 0,
                refundedAmount = 0, productCouponDiscount = allocation.productCouponDiscount,
                cartCouponId = allocation.cartCouponId, cartCouponDiscount = allocation.cartCouponDiscount,
                pointsUsed = allocation.pointsUsed, pointsRefunded = 0, expiresAt = now.plus(paymentTtl), paidAt = null, sellerOrders = sellerOrders,
                createdAt = null,
            )
        }

        fun restore(
            id: Long,
            orderNo: String,
            memberId: Long,
            status: OrderStatus,
            shippingAddress: ShippingAddress,
            itemsAmount: Long,
            buyerShippingFee: Long,
            totalAmount: Long,
            freeShippingApplied: Boolean,
            shippingFeeRecovered: Long,
            refundedAmount: Long,
            productCouponDiscount: Long,
            cartCouponId: Long?,
            cartCouponDiscount: Long,
            pointsUsed: Long,
            pointsRefunded: Long,
            expiresAt: LocalDateTime,
            paidAt: LocalDateTime?,
            sellerOrders: List<SellerOrder>,
            createdAt: LocalDateTime?,
        ) = Order(
            id, orderNo, memberId, status, shippingAddress, itemsAmount, buyerShippingFee, totalAmount,
            freeShippingApplied, shippingFeeRecovered, refundedAmount, productCouponDiscount, cartCouponId,
            cartCouponDiscount, pointsUsed, pointsRefunded, expiresAt, paidAt, sellerOrders, createdAt,
        )
    }
}
