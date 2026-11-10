package com.dogyun.kairos.order.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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

        assertEquals(30_000, plan.paidAmount)
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
    fun 판매자_취소로_이미_깨진_무료배송은_이후_구매자_취소에서_차감하지_않는다() {
        val o = paidOrder(line(1, 30_000), line(2, 20_000, skuId = 21), line(2, 10_000, skuId = 22))

        val sellerCancel = o.cancel(o.itemIdOf(1), CancelActor.SELLER)
        assertEquals(30_000, sellerCancel.refundAmount)
        assertInvariant(o)

        val buyerCancel = o.cancel(o.itemIdOf(2, 1))

        assertEquals(0, buyerCancel.shippingFeeDeducted)
        assertEquals(10_000, buyerCancel.refundAmount)
        assertEquals(0, o.shippingFeeRecovered)
        assertInvariant(o)
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

    @Test
    fun 스펙_예시_c1_취소는_환불_24562_포인트_5625_판매자_쿠폰_반환이다() {
        val o = paidSpecOrder()
        assertEquals(49_000, o.totalAmount)

        val plan = o.cancel(o.itemIdOf(3))

        assertEquals(27_562, plan.paidAmount)
        assertEquals(3_000, plan.shippingFeeDeducted)
        assertEquals(24_562, plan.refundAmount)
        assertEquals(5_625, plan.pointsRefund)
        assertEquals(2L, plan.productCouponReturn)
        assertEquals(5_625, o.pointsRefunded)
        assertEquals(24_562, o.refundedAmount)
        assertEquals(5_625, o.findItem(o.itemIdOf(3)).pointsRefunded)
        assertEquals(o.totalAmount - o.refundedAmount, o.remainingPaidAmount + o.remainingBuyerShippingFee + o.shippingFeeRecovered)
        assertEquals(24_438, o.remainingPaidAmount + o.shippingFeeRecovered)
    }

    @Test
    fun 이어_b1_취소는_24438이고_누적_환불이_49000이다() {
        val o = paidSpecOrder()
        o.cancel(o.itemIdOf(3))

        val plan = o.cancel(o.itemIdOf(2))

        assertEquals(24_438, plan.refundAmount)
        assertEquals(3_000, plan.shippingFeeReturned)
        assertEquals(4_375, plan.pointsRefund)
        assertEquals(1L, plan.productCouponReturn)
        assertEquals(49_000, o.refundedAmount)
        assertEquals(10_000, o.pointsRefunded)
        assertEquals(o.pointsUsed, o.pointsRefunded)
        assertTrue(o.allItemsCanceled)
    }

    @Test
    fun paidAmount가_3000보다_작으면_차감은_현금_환불까지만이고_포인트는_전액_돌아온다() {
        val small = line(sellerId = 2, price = 3_000, skuId = 21)
        val big = line(sellerId = 3, price = 47_000, skuId = 31)
        val allocation = DiscountAllocation(
            lines = mapOf(
                21L to LineAllocation(21, null, 0, null, 0, 2_500),
                31L to LineAllocation(31, null, 0, null, 0, 0),
            ),
            productCouponDiscount = 0, cartCouponId = null, cartCouponDiscount = 0, pointsUsed = 2_500,
        )
        val o = withIds(newOrderWith(allocation, small, big)).also { it.markPaid(NOW) }
        assertTrue(o.freeShippingApplied)

        val plan = o.cancel(o.itemIdOf(2))

        assertEquals(500, plan.paidAmount)
        assertEquals(500, plan.shippingFeeDeducted)
        assertEquals(0, plan.refundAmount)
        assertEquals(2_500, plan.pointsRefund)
        assertEquals(500, o.shippingFeeRecovered)
    }

    @Test
    fun 쿠폰으로_결제액이_4만원이_돼도_itemsAmount가_5만원_이상이면_무료배송이다() {
        val a = line(sellerId = 2, price = 30_000, skuId = 21)
        val b = line(sellerId = 3, price = 20_000, skuId = 31)
        val allocation = DiscountAllocation(
            lines = mapOf(
                21L to LineAllocation(21, 1, 6_000, CouponIssuer.PLATFORM, 0, 0),
                31L to LineAllocation(31, 2, 4_000, CouponIssuer.SELLER, 0, 0),
            ),
            productCouponDiscount = 10_000, cartCouponId = null, cartCouponDiscount = 0, pointsUsed = 0,
        )
        val o = newOrderWith(allocation, a, b)

        assertEquals(50_000, o.itemsAmount)
        assertTrue(o.freeShippingApplied)
        assertEquals(0, o.buyerShippingFee)
        assertEquals(40_000, o.totalAmount)
    }
}
