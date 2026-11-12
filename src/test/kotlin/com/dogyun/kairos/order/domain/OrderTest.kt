package com.dogyun.kairos.order.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrderTest {

    private fun assertCode(code: ErrorCode, block: () -> Unit) {
        assertEquals(code, assertFailsWith<DomainException> { block() }.errorCode)
    }

    private fun soId(o: Order, sellerId: Long) = o.sellerOrders.first { it.sellerId == sellerId }.id!!

    @Test
    fun 판매자별로_묶이고_5만원_이상이면_모든_배송비를_플랫폼이_낸다() {
        val o = newOrder(line(3, 30_000), line(2, 30_000))

        assertEquals(60_000, o.itemsAmount)
        assertEquals(0, o.buyerShippingFee)
        assertEquals(60_000, o.totalAmount)
        assertTrue(o.freeShippingApplied)
        assertEquals(listOf(2L, 3L), o.sellerOrders.map { it.sellerId })
        o.sellerOrders.forEach {
            assertEquals(3_000, it.shippingFee)
            assertEquals(ShippingFeePayer.PLATFORM, it.shippingFeePayer)
            assertEquals(SellerOrderStatus.PENDING_PAYMENT, it.status)
        }
        assertEquals(OrderStatus.PENDING_PAYMENT, o.status)
        assertEquals(NOW.plusMinutes(15), o.expiresAt)
        assertNull(o.paidAt)
    }

    @Test
    fun 같은_판매자_항목은_하나의_판매자_주문으로_묶인다() {
        val o = newOrder(line(1, 10_000, skuId = 11), line(1, 20_000, skuId = 12))

        assertEquals(1, o.sellerOrders.size)
        assertEquals(2, o.sellerOrders[0].items.size)
        assertEquals(3_000, o.buyerShippingFee)
    }

    @Test
    fun 정확히_5만원이면_무료배송이다() {
        val o = newOrder(line(1, 50_000))

        assertTrue(o.freeShippingApplied)
        assertEquals(50_000, o.totalAmount)
    }

    @Test
    fun `5만원_미만이면_판매자_주문마다_구매자가_3000원을_낸다`() {
        val o = newOrder(line(1, 20_000), line(2, 10_000))

        assertFalse(o.freeShippingApplied)
        assertEquals(6_000, o.buyerShippingFee)
        assertEquals(36_000, o.totalAmount)
        o.sellerOrders.forEach {
            assertEquals(3_000, it.shippingFee)
            assertEquals(ShippingFeePayer.BUYER, it.shippingFeePayer)
        }
    }

    @Test
    fun 항목_스냅샷은_상품명_브랜드명_옵션명_판매가_추가금액_판매자ID_수수료율을_복사한다() {
        val o = newOrder(line(5, 10_000, quantity = 3, skuId = 55, extra = 500))

        val so = o.sellerOrders.single()
        val item = so.items.single()
        assertEquals(5L, so.sellerId)
        assertEquals("판매자5", so.sellerName)
        assertEquals(1005, so.commissionRateBp)
        assertEquals(55L, item.skuId)
        assertEquals(1055L, item.productId)
        assertEquals("상품55", item.productName)
        assertEquals("브랜드5", item.brandName)
        assertEquals("블랙 / M", item.optionDisplayName)
        assertEquals(10_000, item.salePrice)
        assertEquals(500, item.extraPrice)
        assertEquals(10_500, item.unitPrice)
        assertEquals(31_500, item.lineAmount)
        assertEquals(OrderItemStatus.ORDERED, item.status)
        assertEquals(31_500, o.itemsAmount)
        assertEquals(31_500 + 3_000, o.totalAmount)
    }

    @Test
    fun 빈_주문_줄이나_잘못된_수량_가격_중복_SKU는_ORDER_INVALID다() {
        assertCode(ErrorCode.ORDER_INVALID) { newOrder() }
        assertCode(ErrorCode.ORDER_INVALID) { newOrder(line(1, 10_000, quantity = 0)) }
        assertCode(ErrorCode.ORDER_INVALID) { newOrder(line(1, 0)) }
        assertCode(ErrorCode.ORDER_INVALID) { newOrder(line(1, 1_000, extra = -1)) }
        assertCode(ErrorCode.ORDER_INVALID) { newOrder(line(1, 1_000, skuId = 11), line(2, 1_000, skuId = 11)) }
    }

    @Test
    fun 배송지는_메모_외_공백이면_ORDER_INVALID다() {
        assertCode(ErrorCode.ORDER_INVALID) { ShippingAddress(" ", "010", "06236", "a", "b", null) }
        assertCode(ErrorCode.ORDER_INVALID) { ShippingAddress("a", "010", "06236", "a", "", null) }
        ShippingAddress("a", "010", "06236", "a", "b", "")
    }

    @Test
    fun 결제하면_주문과_판매자_주문이_PAID다() {
        val o = withIds(newOrder(line(1, 60_000)))

        o.markPaid(NOW.plusMinutes(1))

        assertEquals(OrderStatus.PAID, o.status)
        assertEquals(NOW.plusMinutes(1), o.paidAt)
        assertEquals(SellerOrderStatus.PAID, o.sellerOrders.single().status)
        assertCode(ErrorCode.ORDER_INVALID_STATE_TRANSITION) { o.markPaid(NOW) }
    }

    @Test
    fun 만료하면_주문_판매자주문_항목이_모두_CANCELED_SYSTEM이다() {
        val o = withIds(newOrder(line(1, 20_000), line(2, 10_000)))

        o.expire()

        assertEquals(OrderStatus.CANCELED, o.status)
        o.sellerOrders.forEach { assertEquals(SellerOrderStatus.CANCELED, it.status) }
        o.allItems.forEach {
            assertEquals(OrderItemStatus.CANCELED, it.status)
            assertEquals(CancelActor.SYSTEM, it.cancelActor)
            assertEquals(0, it.refundAmount)
        }
        assertEquals(0, o.refundedAmount)
    }

    @Test
    fun 결제_실패로_취소해도_같다_그리고_PAID_주문은_만료할_수_없다() {
        val o = withIds(newOrder(line(1, 20_000)))
        o.cancelForPaymentFailure()
        assertEquals(OrderStatus.CANCELED, o.status)
        assertEquals(CancelActor.SYSTEM, o.allItems.single().cancelActor)

        val paid = paidOrder(line(1, 20_000))
        assertCode(ErrorCode.ORDER_INVALID_STATE_TRANSITION) { paid.expire() }
        assertCode(ErrorCode.ORDER_INVALID_STATE_TRANSITION) { paid.cancelForPaymentFailure() }
    }

    @Test
    fun PENDING_PAYMENT_주문의_항목_취소_계획은_ORDER_INVALID_STATE_TRANSITION() {
        val o = withIds(newOrder(line(1, 20_000)))

        assertCode(ErrorCode.ORDER_INVALID_STATE_TRANSITION) {
            o.planCancel(o.itemIdOf(1), CancelActor.BUYER, POLICY)
        }
    }

    @Test
    fun SHIPPING_판매자_주문의_항목은_ORDER_ITEM_NOT_CANCELABLE() {
        val o = paidOrder(line(1, 20_000), line(2, 40_000))
        o.ship(soId(o, 1), "CJ", "111", NOW)

        assertCode(ErrorCode.ORDER_ITEM_NOT_CANCELABLE) { o.planCancel(o.itemIdOf(1), CancelActor.BUYER, POLICY) }
    }

    @Test
    fun 이미_취소된_항목과_없는_항목() {
        val o = paidOrder(line(1, 20_000), line(2, 40_000))
        val id = o.itemIdOf(1)
        o.cancel(id)

        assertCode(ErrorCode.ORDER_ITEM_NOT_CANCELABLE) { o.planCancel(id, CancelActor.BUYER, POLICY) }
        assertCode(ErrorCode.ORDER_ITEM_NOT_FOUND) { o.planCancel(999_999, CancelActor.BUYER, POLICY) }
        assertCode(ErrorCode.SELLER_ORDER_NOT_FOUND) { o.ship(999_999, "CJ", "1", NOW) }
    }

    @Test
    fun 판매자_주문_항목이_모두_취소되면_판매자_주문이_CANCELED다() {
        val o = paidOrder(line(1, 10_000, skuId = 11), line(1, 10_000, skuId = 12), line(2, 40_000))

        o.cancel(o.itemIdOf(1, 0))
        assertEquals(SellerOrderStatus.PAID, o.sellerOrders.first { it.sellerId == 1L }.status)
        o.cancel(o.itemIdOf(1, 1))

        assertEquals(SellerOrderStatus.CANCELED, o.sellerOrders.first { it.sellerId == 1L }.status)
        assertEquals(OrderStatus.PAID, o.status)
        assertEquals(40_000, o.remainingItemsAmount)
    }

    @Test
    fun 모든_판매자_주문이_취소되면_주문이_CANCELED다() {
        val o = paidOrder(line(1, 30_000), line(2, 30_000))

        o.cancel(o.itemIdOf(1))
        o.cancel(o.itemIdOf(2))

        assertEquals(OrderStatus.CANCELED, o.status)
    }

    @Test
    fun 확정_전_항목_취소_확정은_ORDER_ITEM_NOT_CANCELABLE이다() {
        val o = paidOrder(line(1, 30_000), line(2, 30_000))
        val id = o.itemIdOf(1)
        val plan = o.planCancel(id, CancelActor.BUYER, POLICY)

        assertCode(ErrorCode.ORDER_ITEM_NOT_CANCELABLE) { o.confirmCancel(id, plan, NOW) }
        assertCode(ErrorCode.ORDER_ITEM_NOT_CANCELABLE) { o.revertCancel(id) }
    }

    @Test
    fun 모든_판매자_주문이_끝나면_COMPLETED다() {
        val o = paidOrder(line(1, 30_000), line(2, 30_000))
        o.cancel(o.itemIdOf(1))
        val b = soId(o, 2)
        o.ship(b, "CJ", "1", NOW)
        o.deliver(b, NOW)
        assertEquals(OrderStatus.PAID, o.status)

        o.confirmPurchase(b, NOW)

        assertEquals(OrderStatus.COMPLETED, o.status)
    }

    @Test
    fun 확정된_판매자_주문이_있는_상태에서_마지막_남은_주문이_취소되면_COMPLETED다() {
        val o = paidOrder(line(1, 30_000), line(2, 30_000))
        val b = soId(o, 2)
        o.ship(b, "CJ", "1", NOW)
        o.deliver(b, NOW)
        o.confirmPurchase(b, NOW)
        assertEquals(OrderStatus.PAID, o.status)

        o.cancel(o.itemIdOf(1))

        assertEquals(OrderStatus.COMPLETED, o.status)
    }

    @Test
    fun 한_판매자_주문이_아직_배송_중이면_주문은_COMPLETED가_아니다() {
        val o = paidOrder(line(1, 30_000), line(2, 30_000))
        val a = soId(o, 1)
        val b = soId(o, 2)
        o.ship(a, "CJ", "1", NOW); o.ship(b, "CJ", "2", NOW)
        o.deliver(a, NOW); o.confirmPurchase(a, NOW)

        assertEquals(OrderStatus.PAID, o.status)
    }

    @Test
    fun 같은_송장으로_다시_ship하면_그대로_SHIPPING이고_다른_송장이면_409() {
        val o = paidOrder(line(1, 60_000))
        val id = soId(o, 1)
        o.ship(id, "CJ", "1234", NOW)

        o.ship(id, "CJ", "1234", NOW.plusHours(1))

        val so = o.findSellerOrder(id)
        assertEquals(SellerOrderStatus.SHIPPING, so.status)
        assertEquals(NOW, so.shippedAt)
        assertCode(ErrorCode.SELLER_ORDER_INVALID_STATE_TRANSITION) { o.ship(id, "CJ", "9999", NOW) }
        assertCode(ErrorCode.SELLER_ORDER_INVALID_STATE_TRANSITION) { o.ship(id, "HANJIN", "1234", NOW) }
    }

    @Test
    fun 취소_확인_중_항목이_있으면_ship은_409() {
        val o = paidOrder(line(1, 30_000, skuId = 11), line(1, 30_000, skuId = 12))
        val itemId = o.itemIdOf(1, 0)
        o.requestCancel(itemId, CancelActor.BUYER, "사유", o.planCancel(itemId, CancelActor.BUYER, POLICY))

        assertCode(ErrorCode.SELLER_ORDER_INVALID_STATE_TRANSITION) { o.ship(soId(o, 1), "CJ", "1", NOW) }
        assertEquals(SellerOrderStatus.PAID, o.findSellerOrder(soId(o, 1)).status)
    }

    @Test
    fun 결제_전_판매자_주문은_ship할_수_없다() {
        val o = withIds(newOrder(line(1, 60_000)))

        assertCode(ErrorCode.SELLER_ORDER_INVALID_STATE_TRANSITION) { o.ship(soId(o, 1), "CJ", "1", NOW) }
    }

    @Test
    fun deliver와_confirm은_이미_목표_상태면_no_op() {
        val o = paidOrder(line(1, 60_000))
        val id = soId(o, 1)
        o.ship(id, "CJ", "1", NOW)
        o.deliver(id, NOW)
        o.deliver(id, NOW.plusDays(1))
        assertEquals(NOW, o.findSellerOrder(id).deliveredAt)

        o.confirmPurchase(id, NOW.plusDays(2))
        o.confirmPurchase(id, NOW.plusDays(3))

        assertEquals(NOW.plusDays(2), o.findSellerOrder(id).confirmedAt)
        assertEquals(OrderStatus.COMPLETED, o.status)
    }

    @Test
    fun 순서를_건너뛴_전이는_409다() {
        val o = paidOrder(line(1, 60_000))
        val id = soId(o, 1)

        assertCode(ErrorCode.SELLER_ORDER_INVALID_STATE_TRANSITION) { o.deliver(id, NOW) }
        assertCode(ErrorCode.SELLER_ORDER_INVALID_STATE_TRANSITION) { o.confirmPurchase(id, NOW) }
    }

    @Test
    fun skuQuantities는_SKU별_수량을_SKU_ID_오름차순으로_합친다() {
        val o = newOrder(line(2, 1_000, quantity = 2, skuId = 30), line(1, 1_000, quantity = 1, skuId = 20), line(1, 1_000, quantity = 4, skuId = 10))

        assertEquals(listOf(10L to 4, 20L to 1, 30L to 2), o.skuQuantities)
    }

    @Test
    fun 남은_구매자_배송비는_취소되지_않은_BUYER_판매자_주문의_합이다() {
        val o = paidOrder(line(1, 20_000), line(2, 10_000))
        assertEquals(6_000, o.remainingBuyerShippingFee)

        o.cancel(o.itemIdOf(2))

        assertEquals(3_000, o.remainingBuyerShippingFee)
        assertEquals(Duration.ofMinutes(15), Duration.between(NOW, o.expiresAt))
    }

    @Test
    fun confirmCancel은_pointsRefunded를_누적한다() {
        val o = paidSpecOrder()

        o.cancel(o.itemIdOf(2))
        assertEquals(4_375, o.pointsRefunded)
        o.cancel(o.itemIdOf(3))

        assertEquals(10_000, o.pointsRefunded)
        assertEquals(4_375, o.findItem(o.itemIdOf(2)).pointsRefunded)
        assertEquals(5_625, o.findItem(o.itemIdOf(3)).pointsRefunded)
    }

    @Test
    fun allItemsCanceled는_모든_항목이_CANCELED일_때만_true다() {
        val o = paidSpecOrder()
        assertFalse(o.allItemsCanceled)

        o.cancel(o.itemIdOf(2))
        assertFalse(o.allItemsCanceled)

        o.cancel(o.itemIdOf(3))
        assertTrue(o.allItemsCanceled)
    }

    @Test
    fun 주문_생성은_할인을_반영해_totalAmount와_사용_쿠폰을_계산한다() {
        val o = newOrderWith(specAllocation(), specB1(), specC1())

        assertEquals(70_000, o.itemsAmount)
        assertEquals(49_000, o.totalAmount)
        assertEquals(6_000, o.productCouponDiscount)
        assertEquals(5_000, o.cartCouponDiscount)
        assertEquals(10_000, o.pointsUsed)
        assertEquals(49_000, o.remainingPaidAmount)
        assertEquals(listOf(1L, 2L, 99L), o.usedCouponIds())
    }

    // ---- 반품(C4) ----

    private val window: Duration = Duration.ofDays(7)

    /** 판매자 [sellerId] 주문을 배송 완료(NOW)까지 진행한 주문. */
    private fun deliveredOrder(vararg lines: OrderLine): Order {
        val o = paidOrder(*lines)
        o.sellerOrders.forEach { o.ship(it.id!!, "CJ", "1", NOW); o.deliver(it.id!!, NOW) }
        return o
    }

    private fun Order.requestReturn(itemId: Long, now: java.time.LocalDateTime = NOW.plusDays(1)) =
        requestReturn(itemId, ReturnReason.CHANGE_OF_MIND, "사이즈", window, now)

    @Test
    fun 배송_완료_7일_1초_전_요청은_성공하고_7일_1초_뒤는_RETURN_WINDOW_EXPIRED() {
        val ok = deliveredOrder(line(1, 30_000))
        val r = ok.requestReturn(ok.itemIdOf(1), NOW.plusDays(7).minusSeconds(1))
        assertEquals(ReturnStatus.REQUESTED, r.status)
        assertEquals(OrderItemStatus.RETURN_REQUESTED, ok.findItem(ok.itemIdOf(1)).status)

        val late = deliveredOrder(line(1, 30_000))
        assertCode(ErrorCode.RETURN_WINDOW_EXPIRED) { late.requestReturn(late.itemIdOf(1), NOW.plusDays(7).plusSeconds(1)) }
    }

    @Test
    fun DELIVERED가_아닌_판매자_주문은_RETURN_NOT_ELIGIBLE() {
        val o = paidOrder(line(1, 30_000))
        assertCode(ErrorCode.RETURN_NOT_ELIGIBLE) { o.requestReturn(o.itemIdOf(1)) }
        o.ship(soId(o, 1), "CJ", "1", NOW)
        assertCode(ErrorCode.RETURN_NOT_ELIGIBLE) { o.requestReturn(o.itemIdOf(1)) }
    }

    @Test
    fun 열린_반품_항목_재요청은_RETURN_ALREADY_REQUESTED() {
        val o = deliveredOrder(line(1, 30_000))
        o.requestReturn(o.itemIdOf(1))
        assertCode(ErrorCode.RETURN_ALREADY_REQUESTED) { o.requestReturn(o.itemIdOf(1)) }
    }

    @Test
    fun 거절된_항목_재요청은_RETURN_ALREADY_REQUESTED() {
        val o = deliveredOrder(line(1, 30_000))
        val r = o.requestReturn(o.itemIdOf(1))
        o.item(r).returnRequest!!.let { o.rejectReturnOf(it) }
        assertEquals(OrderItemStatus.ORDERED, o.findItem(o.itemIdOf(1)).status)
        assertCode(ErrorCode.RETURN_ALREADY_REQUESTED) { o.requestReturn(o.itemIdOf(1)) }
    }

    @Test
    fun 철회_뒤_재요청은_같은_행이_REQUESTED로_돌아온다() {
        val o2 = deliveredOrder(line(1, 30_000))
        val itemId = o2.itemIdOf(1)
        o2.requestReturn(itemId)
        o2.findItem(itemId).returnRequest!!.let { o2.withdrawReturnOf(it) }
        assertEquals(OrderItemStatus.ORDERED, o2.findItem(itemId).status)
        val before = o2.findItem(itemId).returnRequest!!

        val again = o2.requestReturn(itemId, ReturnReason.DEFECTIVE, "불량", window, NOW.plusDays(2))

        assertTrue(before === again)
        assertEquals(ReturnStatus.REQUESTED, again.status)
        assertEquals(ReturnReason.DEFECTIVE, again.reason)
        assertEquals(OrderItemStatus.RETURN_REQUESTED, o2.findItem(itemId).status)
    }

    @Test
    fun 승인_뒤_철회는_RETURN_INVALID_STATE_TRANSITION() {
        val o = deliveredOrder(line(1, 30_000))
        val r = o.requestReturn(o.itemIdOf(1))
        o.findItem(o.itemIdOf(1)).approveReturn(DecidedBy.SELLER, NOW)
        assertCode(ErrorCode.RETURN_INVALID_STATE_TRANSITION) { o.findItem(o.itemIdOf(1)).withdrawReturn() }
        assertEquals(ReturnStatus.APPROVED, r.status)
    }

    @Test
    fun 관리자는_거절된_반품을_승인할_수_있고_CONFIRMED_뒤는_RETURN_NOT_ELIGIBLE() {
        val o = deliveredOrder(line(1, 30_000))
        val itemId = o.itemIdOf(1)
        val r = o.requestReturn(itemId)
        o.findItem(itemId).rejectReturn("상태 불량 아님", NOW)
        assertCode(ErrorCode.RETURN_INVALID_STATE_TRANSITION) { r.approve(DecidedBy.SELLER, NOW) }

        r.approve(DecidedBy.ADMIN, NOW)
        assertEquals(ReturnStatus.APPROVED, r.status)

        val withId = ReturnRequest(
            5L, 1L, 101L, 1000L, 7L, 1L, ReturnReason.CHANGE_OF_MIND, null, ReturnStatus.REJECTED, 0, 0, 0, 0, 0, 0,
            "거절", NOW, NOW, DecidedBy.SELLER, null, null,
        )
        val c2 = deliveredOrderWithReturn(withId)
        c2.confirmPurchase(soId(c2, 1), NOW)
        assertCode(ErrorCode.RETURN_NOT_ELIGIBLE) { c2.approveReturn(5L, DecidedBy.ADMIN, NOW) }
    }

    @Test
    fun 열린_반품이_있으면_confirm은_SELLER_ORDER_HAS_OPEN_RETURN() {
        val o = deliveredOrder(line(1, 30_000))
        o.requestReturn(o.itemIdOf(1))
        assertCode(ErrorCode.SELLER_ORDER_HAS_OPEN_RETURN) { o.confirmPurchase(soId(o, 1), NOW) }
        assertTrue(o.sellerOrders.first().hasOpenReturn)
    }

    @Test
    fun remainingItemsAmount는_열린_반품_항목을_포함한다() {
        val o = deliveredOrder(line(1, 30_000), line(2, 30_000))
        assertEquals(60_000, o.remainingItemsAmount)
        o.requestReturn(o.itemIdOf(1))
        assertEquals(60_000, o.remainingItemsAmount)
        assertEquals(60_000, o.remainingPaidAmount)
        assertEquals(30_000, o.sellerOrders.first().remainingItemsAmount)
    }

    /** id 가 붙은 [request] 를 첫 항목에 달아 둔 배송 완료 주문. */
    private fun deliveredOrderWithReturn(request: ReturnRequest): Order {
        val o = deliveredOrder(line(1, 30_000))
        val so = o.sellerOrders.first()
        val old = so.items.first()
        val item = OrderItem(
            old.id, old.skuId, old.productId, old.productName, old.brandName, old.optionDisplayName, old.salePrice, old.extraPrice,
            old.quantity, OrderItemStatus.ORDERED, null, null, 0, 0, 0, null, null, 0, null, 0, 0, old.paidAmount, 0, request,
        )
        val rebuilt = SellerOrder(
            so.id, so.sellerId, so.sellerName, so.commissionRateBp, so.status, so.shippingFee, so.shippingFeePayer,
            so.carrierCode, so.trackingNumber, so.shippedAt, so.deliveredAt, so.confirmedAt, listOf(item),
        )
        return Order(
            o.id, o.orderNo, o.memberId, o.status, o.shippingAddress, o.itemsAmount, o.buyerShippingFee, o.totalAmount,
            o.freeShippingApplied, o.shippingFeeRecovered, o.refundedAmount, o.productCouponDiscount, o.cartCouponId,
            o.cartCouponDiscount, o.pointsUsed, o.pointsRefunded, o.expiresAt, o.paidAt, listOf(rebuilt), NOW,
        )
    }

    private fun Order.item(r: ReturnRequest) = findItem(r.orderItemId)
    private fun Order.rejectReturnOf(r: ReturnRequest) = item(r).rejectReturn("거절", NOW)
    private fun Order.withdrawReturnOf(r: ReturnRequest) = item(r).withdrawReturn()

    // ---- 반품 자격 판정(checkReturnRequestable) ----

    private fun Order.checkRequestable(itemId: Long, now: java.time.LocalDateTime = NOW.plusDays(1)) =
        checkReturnRequestable(itemId, window, now)

    @Test
    fun 자격_판정_열린_반품은_RETURN_ALREADY_REQUESTED() {
        val o = deliveredOrder(line(1, 30_000))
        o.requestReturn(o.itemIdOf(1))
        assertCode(ErrorCode.RETURN_ALREADY_REQUESTED) { o.checkRequestable(o.itemIdOf(1)) }
    }

    @Test
    fun 자격_판정_판매자_주문이_DELIVERED가_아니면_RETURN_NOT_ELIGIBLE() {
        val o = paidOrder(line(1, 30_000))
        assertCode(ErrorCode.RETURN_NOT_ELIGIBLE) { o.checkRequestable(o.itemIdOf(1)) }
        o.ship(soId(o, 1), "CJ", "1", NOW)
        assertCode(ErrorCode.RETURN_NOT_ELIGIBLE) { o.checkRequestable(o.itemIdOf(1)) }
    }

    @Test
    fun 자격_판정_기한_정각은_통과하고_1초_뒤는_RETURN_WINDOW_EXPIRED() {
        val o = deliveredOrder(line(1, 30_000))
        o.checkRequestable(o.itemIdOf(1), NOW.plusDays(7))
        assertCode(ErrorCode.RETURN_WINDOW_EXPIRED) { o.checkRequestable(o.itemIdOf(1), NOW.plusDays(7).plusSeconds(1)) }
    }

    @Test
    fun 자격_판정_거절된_반품은_RETURN_ALREADY_REQUESTED() {
        val o = deliveredOrder(line(1, 30_000))
        val r = o.requestReturn(o.itemIdOf(1))
        o.rejectReturnOf(r)
        assertCode(ErrorCode.RETURN_ALREADY_REQUESTED) { o.checkRequestable(o.itemIdOf(1)) }
    }

    @Test
    fun 자격_판정_관리자가_중단한_반품은_RETURN_ALREADY_REQUESTED() {
        val o = deliveredOrder(line(1, 30_000))
        val r = o.requestReturn(o.itemIdOf(1))
        o.item(r).approveReturn(DecidedBy.SELLER, NOW)
        o.item(r).cancelReturnByAdmin()
        assertEquals(ReturnStatus.CANCELED_BY_ADMIN, o.item(r).returnRequest!!.status)
        assertCode(ErrorCode.RETURN_ALREADY_REQUESTED) { o.checkRequestable(o.itemIdOf(1)) }
    }

    @Test
    fun 자격_판정_철회한_반품은_통과한다() {
        val o = deliveredOrder(line(1, 30_000))
        val r = o.requestReturn(o.itemIdOf(1))
        o.withdrawReturnOf(r)
        o.checkRequestable(o.itemIdOf(1))
    }

    @Test
    fun 자격_판정_검사_순서_열린_반품이면서_기한도_지났으면_RETURN_ALREADY_REQUESTED() {
        val o = deliveredOrder(line(1, 30_000))
        o.requestReturn(o.itemIdOf(1))
        assertCode(ErrorCode.RETURN_ALREADY_REQUESTED) { o.checkRequestable(o.itemIdOf(1), NOW.plusDays(30)) }
    }

    @Test
    fun 자격_판정_검사_순서_상태가_맞지_않으면서_기한도_지났으면_RETURN_NOT_ELIGIBLE() {
        val o = paidOrder(line(1, 30_000))
        assertCode(ErrorCode.RETURN_NOT_ELIGIBLE) { o.checkRequestable(o.itemIdOf(1), NOW.plusDays(30)) }
    }

    @Test
    fun 자격_판정_기한이_지났고_거절된_반품이면_RETURN_WINDOW_EXPIRED가_먼저다() {
        val o = deliveredOrder(line(1, 30_000))
        val r = o.requestReturn(o.itemIdOf(1))
        o.rejectReturnOf(r)
        assertCode(ErrorCode.RETURN_WINDOW_EXPIRED) { o.checkRequestable(o.itemIdOf(1), NOW.plusDays(30)) }
    }

    @Test
    fun 자격_판정은_없는_항목이면_ORDER_ITEM_NOT_FOUND() {
        val o = deliveredOrder(line(1, 30_000))
        assertCode(ErrorCode.ORDER_ITEM_NOT_FOUND) { o.checkRequestable(999_999L) }
    }

    @Test
    fun 자격_판정은_주문_상태를_바꾸지_않는다() {
        val o = deliveredOrder(line(1, 30_000))
        val itemId = o.itemIdOf(1)

        o.checkRequestable(itemId)

        assertEquals(OrderStatus.PAID, o.status)
        assertEquals(OrderItemStatus.ORDERED, o.findItem(itemId).status)
        assertEquals(null, o.findItem(itemId).returnRequest)
    }
}
