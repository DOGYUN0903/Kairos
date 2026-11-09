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

    val allItems: List<OrderItem> get() = sellerOrders.flatMap { it.items }
    val remainingItemsAmount: Long get() = sellerOrders.sumOf { it.remainingItemsAmount }
    val remainingBuyerShippingFee: Long
        get() = sellerOrders
            .filter { it.status != SellerOrderStatus.CANCELED && it.shippingFeePayer == ShippingFeePayer.BUYER }
            .sumOf { it.shippingFee }

    /** SKU별 수량 합계, SKU ID 오름차순(재고 복구 순서). */
    val skuQuantities: List<Pair<Long, Int>>
        get() = allItems.groupBy { it.skuId }.map { (sku, items) -> sku to items.sumOf { it.quantity } }.sortedBy { it.first }

    fun findItem(itemId: Long): OrderItem =
        allItems.firstOrNull { it.id == itemId } ?: throw DomainException(ErrorCode.ORDER_ITEM_NOT_FOUND)

    fun findSellerOrder(id: Long): SellerOrder =
        sellerOrders.firstOrNull { it.id == id } ?: throw DomainException(ErrorCode.SELLER_ORDER_NOT_FOUND)

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
        item.confirmCancel(now)
        refundedAmount += plan.refundAmount
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
                        OrderItem(
                            id = null, skuId = it.skuId, productId = it.productId, productName = it.productName,
                            brandName = it.brandName, optionDisplayName = it.optionDisplayName, salePrice = it.salePrice,
                            extraPrice = it.extraPrice, quantity = it.quantity, status = OrderItemStatus.ORDERED,
                            cancelActor = null, cancelReason = null, refundAmount = 0, shippingFeeDeducted = 0,
                            shippingFeeReturned = 0, canceledAt = null,
                        )
                    },
                )
            }
            val buyerShippingFee = sellerOrders.filter { it.shippingFeePayer == ShippingFeePayer.BUYER }.sumOf { it.shippingFee }
            return Order(
                id = null, orderNo = orderNo, memberId = memberId, status = OrderStatus.PENDING_PAYMENT,
                shippingAddress = address, itemsAmount = itemsAmount, buyerShippingFee = buyerShippingFee,
                totalAmount = itemsAmount + buyerShippingFee, freeShippingApplied = free, shippingFeeRecovered = 0,
                refundedAmount = 0, expiresAt = now.plus(paymentTtl), paidAt = null, sellerOrders = sellerOrders,
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
            expiresAt: LocalDateTime,
            paidAt: LocalDateTime?,
            sellerOrders: List<SellerOrder>,
            createdAt: LocalDateTime?,
        ) = Order(
            id, orderNo, memberId, status, shippingAddress, itemsAmount, buyerShippingFee, totalAmount,
            freeShippingApplied, shippingFeeRecovered, refundedAmount, expiresAt, paidAt, sellerOrders, createdAt,
        )
    }
}
