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

internal fun newOrder(vararg lines: OrderLine) =
    Order.create("K20261110120000ABC123", 7L, lines.toList(), ADDRESS, POLICY, NOW, Duration.ofMinutes(15))

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
                    it.refundAmount, it.shippingFeeDeducted, it.shippingFeeReturned, it.canceledAt,
                )
            },
        )
    }
    return Order(
        1L, o.orderNo, o.memberId, o.status, o.shippingAddress, o.itemsAmount, o.buyerShippingFee, o.totalAmount,
        o.freeShippingApplied, o.shippingFeeRecovered, o.refundedAmount, o.expiresAt, o.paidAt, sellerOrders, NOW,
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
