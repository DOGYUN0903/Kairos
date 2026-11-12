package com.dogyun.kairos.order.domain

import java.time.Duration
import java.time.LocalDateTime

internal val POLICY = ShippingPolicy(50_000, 3_000)
internal val NOW: LocalDateTime = LocalDateTime.of(2026, 11, 10, 12, 0, 0)
internal val ADDRESS = ShippingAddress("홍길동", "010-1234-5678", "06236", "서울 강남구 테헤란로 1", "101호", null)

/** 판매자 [sellerId]의 단가 [price] 한 개짜리 주문 줄. skuId는 sellerId*10+index로 겹치지 않게 만든다. */
internal fun line(sellerId: Long, price: Long, quantity: Int = 1, skuId: Long = sellerId * 10, extra: Long = 0) = OrderLine(
    skuId = skuId, productId = skuId + 1000, productName = "상품$skuId", brandName = "브랜드$sellerId",
    optionDisplayName = "블랙 / M", salePrice = price, extraPrice = extra, sellerId = sellerId,
    sellerName = "판매자$sellerId", commissionRateBp = 1000 + sellerId.toInt(), quantity = quantity,
)

internal fun newOrder(vararg lines: OrderLine) = newOrderWith(DiscountAllocation.none(lines.toList()), *lines)

internal fun newOrderWith(allocation: DiscountAllocation, vararg lines: OrderLine) =
    Order.create("K20261110120000ABC123", 7L, lines.toList(), allocation, ADDRESS, POLICY, NOW, Duration.ofMinutes(15))

/** create 결과(id 없음)에 순차 id를 붙여 DB에서 읽은 것처럼 만든다. 판매자 주문 id 100+, 항목 id 1000+. */
internal fun withIds(o: Order): Order {
    var itemSeq = 1000L
    var soSeq = 100L
    val sellerOrders = o.sellerOrders.map { so ->
        SellerOrder(
            soSeq++, so.sellerId, so.sellerName, so.commissionRateBp, so.status, so.shippingFee, so.shippingFeePayer,
            so.carrierCode, so.trackingNumber, so.shippedAt, so.deliveredAt, so.confirmedAt,
            so.items.map {
                OrderItem(
                    itemSeq++, it.skuId, it.productId, it.productName, it.brandName, it.optionDisplayName,
                    it.salePrice, it.extraPrice, it.quantity, it.status, it.cancelActor, it.cancelReason,
                    it.refundAmount, it.shippingFeeDeducted, it.shippingFeeReturned, it.canceledAt, it.productCouponId,
                    it.productCouponDiscount, it.productCouponIssuer, it.cartCouponAllocated, it.pointsAllocated, it.paidAmount,
                    it.pointsRefunded, it.returnRequest,
                )
            },
        )
    }
    return Order(
        1L, o.orderNo, o.memberId, o.status, o.shippingAddress, o.itemsAmount, o.buyerShippingFee, o.totalAmount,
        o.freeShippingApplied, o.shippingFeeRecovered, o.refundedAmount, o.productCouponDiscount, o.cartCouponId,
        o.cartCouponDiscount, o.pointsUsed, o.pointsRefunded, o.expiresAt, o.paidAt, sellerOrders, NOW,
    )
}

internal fun paidOrder(vararg lines: OrderLine): Order = withIds(newOrder(*lines)).also { it.markPaid(NOW) }

/** 계획 → 요청 → 확정을 한 번에 수행하고 계획을 돌려준다. */
internal fun Order.cancel(itemId: Long, actor: CancelActor = CancelActor.BUYER): RefundPlan {
    val plan = planCancel(itemId, actor, POLICY)
    requestCancel(itemId, actor, "사유", plan)
    confirmCancel(itemId, plan, NOW)
    return plan
}

internal fun Order.itemIdOf(sellerId: Long, index: Int = 0): Long =
    sellerOrders.first { it.sellerId == sellerId }.items[index].id!!

/** 스펙 6장 예시 주문 줄: b1(판매자 2, 30,000×1, sku 21), c1(판매자 3, 20,000×2, sku 31). */
internal fun specB1() = line(sellerId = 2, price = 30_000, skuId = 21)
internal fun specC1() = line(sellerId = 3, price = 20_000, quantity = 2, skuId = 31)

/** 스펙 6장 예시 배분: 상품 쿠폰 2,000/4,000, 장바구니 2,187/2,813, 포인트 4,375/5,625 (paidAmount 21,438/27,562). */
internal fun specAllocation(): DiscountAllocation {
    val product = { id: Long, issuer: CouponIssuer, type: CouponDiscountType, value: Long, max: Long? ->
        CouponTerms(id, CouponKind.PRODUCT, issuer, CouponTarget.ALL_PRODUCTS, null, null, type, value, max, null)
    }
    return DiscountAllocator.allocate(
        lines = listOf(specB1(), specC1()),
        productCoupons = mapOf(
            21L to product(1, CouponIssuer.PLATFORM, CouponDiscountType.FIXED, 2_000, null),
            31L to product(2, CouponIssuer.SELLER, CouponDiscountType.RATE, 1_000, 5_000),
        ),
        cartCoupon = CouponTerms(
            99, CouponKind.CART, CouponIssuer.PLATFORM, CouponTarget.ALL_PRODUCTS, null, null,
            CouponDiscountType.RATE, 1_000, 5_000, 50_000,
        ),
        pointsToUse = 10_000, pointBalance = 20_000,
        policy = PointUsePolicy(minUsableBalance = 1_000, useUnit = 10, maxUseRateBp = 3_000),
    )
}

internal fun paidSpecOrder(): Order =
    withIds(newOrderWith(specAllocation(), specB1(), specC1())).also { it.markPaid(NOW) }

internal val RETURN_FEES = ReturnFeePolicy(returnFee = 3_000, outboundFee = 3_000)

/** 결제(PAID) 상태인 판매자 주문을 모두 배송 완료(DELIVERED, deliveredAt = NOW)로 만든다. */
internal fun Order.deliverAll(): Order = also { o ->
    o.sellerOrders.filter { it.status == SellerOrderStatus.PAID }.forEach {
        o.ship(it.id!!, "04", "T${it.id}", NOW)
        o.deliver(it.id!!, NOW)
    }
}

/** 모든 판매자 주문이 배송 완료된 주문. */
internal fun deliveredOrder(vararg lines: OrderLine): Order = paidOrder(*lines).deliverAll()

/** 반품 요청 id 는 항목 id + 5000. */
internal fun returnIdOf(itemId: Long) = itemId + 5_000

/**
 * 항목 [itemId] 에 반품을 만들어 [status] 까지 진행한다(REQUESTED·APPROVED·REFUNDING·COMPLETED).
 * REFUNDING·COMPLETED 는 [RETURN_FEES] 로 계산한 계획을 쓴다. COMPLETED 는 항목만 RETURNED 로 바꾼다(주문 누적 금액 반영은 하지 않음).
 */
internal fun Order.withReturn(itemId: Long, reason: ReturnReason, status: ReturnStatus = ReturnStatus.APPROVED): Order = also { o ->
    val so = o.sellerOrders.first { s -> s.items.any { it.id == itemId } }
    val item = o.findItem(itemId)
    item.requestReturn(
        ReturnRequest(
            returnIdOf(itemId), o.id!!, so.id!!, itemId, o.memberId, so.sellerId, reason, null, ReturnStatus.REQUESTED,
            0, 0, 0, 0, 0, 0, null, NOW, null, null, null, null,
        ),
    )
    if (status == ReturnStatus.REQUESTED) return@also
    o.approveReturn(returnIdOf(itemId), DecidedBy.SELLER, NOW)
    if (status == ReturnStatus.APPROVED) return@also
    o.startReturnRefund(returnIdOf(itemId), o.planReturnRefund(returnIdOf(itemId), RETURN_FEES), NOW)
    if (status == ReturnStatus.REFUNDING) return@also
    check(status == ReturnStatus.COMPLETED)
    item.completeReturn(NOW, item.pointsAllocated)
}

/**
 * DB 에서 읽은 것처럼 주문을 복원하되 일부 값만 바꾼다(재구성 생성자 경로). 정상 흐름으로는 만들 수 없는 상태를 만들어
 * 계산기의 방어 검사(상태 가드·불변식)를 확인할 때 쓴다.
 */
internal fun Order.restored(
    status: OrderStatus = this.status,
    freeShippingApplied: Boolean = this.freeShippingApplied,
    refundedAmount: Long = this.refundedAmount,
    pointsRefunded: Long = this.pointsRefunded,
    sellerOrderStatus: (SellerOrder) -> SellerOrderStatus = { it.status },
    shippingFeePayer: (SellerOrder) -> ShippingFeePayer = { it.shippingFeePayer },
    item: (OrderItem) -> OrderItem = { it },
): Order = Order(
    id, orderNo, memberId, status, shippingAddress, itemsAmount, buyerShippingFee, totalAmount,
    freeShippingApplied, shippingFeeRecovered, refundedAmount, productCouponDiscount, cartCouponId,
    cartCouponDiscount, pointsUsed, pointsRefunded, expiresAt, paidAt,
    sellerOrders.map { so ->
        SellerOrder(
            so.id, so.sellerId, so.sellerName, so.commissionRateBp, sellerOrderStatus(so), so.shippingFee, shippingFeePayer(so),
            so.carrierCode, so.trackingNumber, so.shippedAt, so.deliveredAt, so.confirmedAt, so.items.map(item),
        )
    },
    NOW,
)

internal fun OrderItem.restoredItem(
    status: OrderItemStatus = this.status,
    shippingFeeReturned: Long = this.shippingFeeReturned,
    returnRequest: ReturnRequest? = this.returnRequest,
) = OrderItem(
    id, skuId, productId, productName, brandName, optionDisplayName, salePrice, extraPrice, quantity, status,
    cancelActor, cancelReason, refundAmount, shippingFeeDeducted, shippingFeeReturned, canceledAt, productCouponId,
    productCouponDiscount, productCouponIssuer, cartCouponAllocated, pointsAllocated, paidAmount, pointsRefunded, returnRequest,
)

internal fun ReturnRequest.restoredWithStatus(status: ReturnStatus) = ReturnRequest(
    id, orderId, sellerOrderId, orderItemId, memberId, sellerId, reason, detail, status, returnFee, outboundFeeRecovered,
    shippingFeeRefund, sellerBorneFee, refundAmount, pointsRefund, rejectReason, requestedAt, decidedAt, decidedBy,
    receivedAt, completedAt,
)
