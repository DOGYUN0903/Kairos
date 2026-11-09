package com.dogyun.kairos.order.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RefundCalculatorTest {

    private fun assertCode(code: ErrorCode, block: () -> Unit) {
        assertEquals(code, assertFailsWith<DomainException> { block() }.errorCode)
    }

    private fun assertInvariant(o: Order) {
        assertEquals(
            o.totalAmount - o.refundedAmount,
            o.remainingItemsAmount + o.remainingBuyerShippingFee + o.shippingFeeRecovered,
        )
    }

    @Test
    fun 스펙_예시_B3만_C3만_무료배송에서_C_취소는_27000원_환불_회수_3000() {
        val o = paidOrder(line(2, 30_000), line(3, 30_000))

        val plan = o.cancel(o.itemIdOf(3))

        assertEquals(30_000, plan.itemAmount)
        assertEquals(0, plan.shippingFeeRefund)
        assertEquals(3_000, plan.shippingFeeDeducted)
        assertEquals(27_000, plan.refundAmount)
        val b = o.sellerOrders.first { it.sellerId == 2L }
        assertEquals(3_000, b.shippingFee)
        assertEquals(ShippingFeePayer.PLATFORM, b.shippingFeePayer)
        assertEquals(SellerOrderStatus.PAID, b.status)
        assertEquals(3_000, o.shippingFeeRecovered)
    }

    @Test
    fun 구매자가_배송비를_낸_판매자_주문이_전부_취소되면_배송비도_환불한다() {
        val o = paidOrder(line(1, 20_000), line(2, 10_000))

        val plan = o.planCancel(o.itemIdOf(2), CancelActor.BUYER, POLICY)

        assertEquals(3_000, plan.shippingFeeRefund)
        assertEquals(0, plan.shippingFeeDeducted)
        assertEquals(13_000, plan.refundAmount)
    }

    @Test
    fun 차감은_한_주문에서_한_번만이다() {
        val o = paidOrder(line(2, 30_000), line(3, 20_000), line(4, 10_000))

        val first = o.cancel(o.itemIdOf(3))
        val second = o.cancel(o.itemIdOf(4))

        assertEquals(3_000, first.shippingFeeDeducted)
        assertEquals(17_000, first.refundAmount)
        assertEquals(0, second.shippingFeeDeducted)
        assertEquals(10_000, second.refundAmount)
    }

    @Test
    fun 남은_금액이_정확히_5만원이면_차감하지_않는다() {
        val o = paidOrder(line(2, 30_000), line(3, 20_000), line(4, 10_000))

        val plan = o.planCancel(o.itemIdOf(4), CancelActor.BUYER, POLICY)

        assertEquals(0, plan.shippingFeeDeducted)
        assertEquals(10_000, plan.refundAmount)
    }

    @Test
    fun 판매자_취소는_차감_없이_전액_환불한다() {
        val o = paidOrder(line(2, 30_000), line(3, 30_000))

        val plan = o.planCancel(o.itemIdOf(3), CancelActor.SELLER, POLICY)

        assertEquals(0, plan.shippingFeeDeducted)
        assertEquals(30_000, plan.refundAmount)
    }

    @Test
    fun 관리자_취소도_차감하지_않는다() {
        val o = paidOrder(line(2, 30_000), line(3, 30_000))

        val plan = o.planCancel(o.itemIdOf(3), CancelActor.ADMIN, POLICY)

        assertEquals(0, plan.shippingFeeDeducted)
        assertEquals(30_000, plan.refundAmount)
    }

    @Test
    fun 차감이_환불액을_넘지_않아_환불_0원이_된다() {
        val o = paidOrder(line(1, 48_000), line(2, 2_000))

        val plan = o.cancel(o.itemIdOf(2))

        assertEquals(2_000, plan.shippingFeeDeducted)
        assertEquals(0, plan.refundAmount)
    }

    @Test
    fun 무료배송_주문을_전부_취소하면_마지막_취소에서_차감했던_배송비를_돌려주고_누적_환불이_결제_금액과_같다() {
        val o = paidOrder(line(2, 30_000), line(3, 30_000))

        val first = o.cancel(o.itemIdOf(3))
        val last = o.cancel(o.itemIdOf(2))

        assertEquals(27_000, first.refundAmount)
        assertEquals(0, last.shippingFeeDeducted)
        assertEquals(3_000, last.shippingFeeReturned)
        assertEquals(33_000, last.refundAmount)
        assertEquals(60_000, o.refundedAmount)
        assertEquals(0, o.shippingFeeRecovered)
        assertEquals(OrderStatus.CANCELED, o.status)
    }

    @Test
    fun 구매자_배송비_주문을_전부_취소하면_배송비까지_환불된다() {
        val o = paidOrder(line(1, 20_000), line(2, 10_000))
        assertEquals(36_000, o.totalAmount)

        assertEquals(13_000, o.cancel(o.itemIdOf(2)).refundAmount)
        assertEquals(23_000, o.cancel(o.itemIdOf(1)).refundAmount)

        assertEquals(36_000, o.refundedAmount)
    }

    @Test
    fun 불변식_결제금액_빼기_누적환불은_남은_상품금액_더하기_남은_구매자_배송비_더하기_회수금과_같다() {
        val free = paidOrder(line(1, 20_000), line(2, 10_000), line(3, 30_000))
        assertInvariant(free)
        free.cancel(free.itemIdOf(3)); assertInvariant(free)
        free.cancel(free.itemIdOf(1)); assertInvariant(free)
        free.cancel(free.itemIdOf(2)); assertInvariant(free)

        val paid = paidOrder(line(1, 20_000), line(2, 10_000))
        assertInvariant(paid)
        paid.cancel(paid.itemIdOf(2)); assertInvariant(paid)
        paid.cancel(paid.itemIdOf(1)); assertInvariant(paid)
    }

    @Test
    fun 같은_판매자_항목이_여럿이면_마지막_항목_취소에서만_구매자_배송비를_환불한다() {
        val o = paidOrder(line(1, 10_000, skuId = 11), line(1, 10_000, skuId = 12))

        val first = o.cancel(o.itemIdOf(1, 0))
        val second = o.cancel(o.itemIdOf(1, 1))

        assertEquals(0, first.shippingFeeRefund)
        assertEquals(3_000, second.shippingFeeRefund)
        assertEquals(o.totalAmount, o.refundedAmount)
    }

    @Test
    fun 다른_항목의_취소_확인_중에는_새_취소_계획을_거절한다() {
        val o = paidOrder(line(2, 30_000), line(3, 30_000))
        val plan = o.planCancel(o.itemIdOf(3), CancelActor.BUYER, POLICY)
        o.requestCancel(o.itemIdOf(3), CancelActor.BUYER, "사유", plan)

        assertCode(ErrorCode.ORDER_ITEM_NOT_CANCELABLE) { o.planCancel(o.itemIdOf(2), CancelActor.BUYER, POLICY) }
    }

    @Test
    fun 취소_요청을_되돌리면_항목은_ORDERED이고_환불_기록이_지워진다() {
        val o = paidOrder(line(2, 30_000), line(3, 30_000))
        val id = o.itemIdOf(3)
        o.requestCancel(id, CancelActor.BUYER, "사유", o.planCancel(id, CancelActor.BUYER, POLICY))

        o.revertCancel(id)

        val item = o.findItem(id)
        assertEquals(OrderItemStatus.ORDERED, item.status)
        assertEquals(0, item.refundAmount)
        assertEquals(0, item.shippingFeeDeducted)
        assertEquals(0, o.refundedAmount)
    }

    @Test
    fun 요청_때_저장한_값에서_환불_계획을_복원한다() {
        val o = paidOrder(line(1, 20_000), line(2, 10_000))
        val id = o.itemIdOf(2)
        val plan = o.planCancel(id, CancelActor.BUYER, POLICY)
        o.requestCancel(id, CancelActor.BUYER, "사유", plan)

        assertEquals(plan, o.findItem(id).toRefundPlan())
    }

    @Test
    fun 판매자_주문이_아직_PAID이면_다른_판매자가_배송_중이어도_이_항목은_취소할_수_있다() {
        val o = paidOrder(line(2, 30_000), line(3, 30_000))
        o.ship(o.sellerOrders.first { it.sellerId == 2L }.id!!, "CJ", "1234", NOW)

        val plan = o.cancel(o.itemIdOf(3))

        assertEquals(27_000, plan.refundAmount)
        assertEquals(OrderStatus.PAID, o.status)
    }
}
