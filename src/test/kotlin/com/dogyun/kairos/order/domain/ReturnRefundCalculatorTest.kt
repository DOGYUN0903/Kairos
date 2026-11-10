package com.dogyun.kairos.order.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReturnRefundCalculatorTest {

    private fun assertCode(code: ErrorCode, block: () -> Unit) {
        assertEquals(code, assertFailsWith<DomainException> { block() }.errorCode)
    }

    private fun Order.plan(itemId: Long) = planReturnRefund(returnIdOf(itemId), RETURN_FEES)

    @Test
    fun 예시A_a2_변심은_환불_14000_returnFee_3000_outbound_3000() {
        val o = deliveredOrder(line(1, 40_000, skuId = 11), line(1, 20_000, skuId = 12))
        val a2 = o.itemIdOf(1, 1)
        o.withReturn(a2, ReturnReason.CHANGE_OF_MIND)

        val plan = o.plan(a2)

        assertEquals(3_000, plan.returnFee)
        assertEquals(3_000, plan.outboundFeeRecovered)
        assertEquals(0, plan.shippingFeeRefund)
        assertEquals(0, plan.sellerBorneFee)
        assertEquals(14_000, plan.refundAmount)
    }

    @Test
    fun 예시A_a1_이어_변심은_환불_37000_outbound_0() {
        val o = deliveredOrder(line(1, 40_000, skuId = 11), line(1, 20_000, skuId = 12))
        val a1 = o.itemIdOf(1, 0)
        o.withReturn(o.itemIdOf(1, 1), ReturnReason.CHANGE_OF_MIND, ReturnStatus.COMPLETED)
        o.withReturn(a1, ReturnReason.CHANGE_OF_MIND)

        val plan = o.plan(a1)

        assertEquals(3_000, plan.returnFee)
        assertEquals(0, plan.outboundFeeRecovered)
        assertEquals(0, plan.shippingFeeRefund)
        assertEquals(37_000, plan.refundAmount)
    }

    @Test
    fun 예시B_불량은_환불_33000_sellerBorne_3000_shippingFeeRefund_3000() {
        val o = deliveredOrder(line(2, 30_000))
        val b1 = o.itemIdOf(2)
        o.withReturn(b1, ReturnReason.DEFECTIVE)

        val plan = o.plan(b1)

        assertEquals(0, plan.returnFee)
        assertEquals(0, plan.outboundFeeRecovered)
        assertEquals(3_000, plan.sellerBorneFee)
        assertEquals(3_000, plan.shippingFeeRefund)
        assertEquals(33_000, plan.refundAmount)
        assertEquals(o.totalAmount, plan.refundAmount)
    }

    @Test
    fun 예시C_취소_차감_뒤_변심_반품은_outbound_0_환불_27000() {
        val o = paidOrder(line(2, 30_000), line(3, 30_000))
        assertEquals(3_000, o.cancel(o.itemIdOf(3)).shippingFeeDeducted)
        o.deliverAll()
        val b = o.itemIdOf(2)
        o.withReturn(b, ReturnReason.CHANGE_OF_MIND)

        val plan = o.plan(b)

        assertEquals(3_000, plan.returnFee)
        assertEquals(0, plan.outboundFeeRecovered)
        assertEquals(27_000, plan.refundAmount)
        assertEquals(54_000, o.refundedAmount + plan.refundAmount)
    }

    @Test
    fun 예시D_포인트_쿠폰_항목_불량은_환불_38000_포인트_3000_쿠폰_반환() {
        val d1 = line(4, 50_000, skuId = 41)
        val allocation = DiscountAllocator.allocate(
            lines = listOf(d1),
            productCoupons = mapOf(
                41L to CouponTerms(7, CouponKind.PRODUCT, CouponIssuer.SELLER, CouponTarget.ALL_PRODUCTS, null, null, CouponDiscountType.FIXED, 5_000, null, null),
            ),
            cartCoupon = CouponTerms(8, CouponKind.CART, CouponIssuer.PLATFORM, CouponTarget.ALL_PRODUCTS, null, null, CouponDiscountType.FIXED, 4_000, null, null),
            pointsToUse = 3_000, pointBalance = 10_000,
            policy = PointUsePolicy(minUsableBalance = 1_000, useUnit = 10, maxUseRateBp = 3_000),
        )
        val o = withIds(newOrderWith(allocation, d1)).also { it.markPaid(NOW) }.deliverAll()
        val id = o.itemIdOf(4)
        assertEquals(38_000, o.findItem(id).paidAmount)
        o.withReturn(id, ReturnReason.DEFECTIVE)

        val plan = o.plan(id)

        assertEquals(38_000, plan.refundAmount)
        assertEquals(3_000, plan.pointsRefund)
        assertEquals(7L, plan.productCouponReturn)
        assertEquals(3_000, plan.sellerBorneFee)
        assertEquals(0, plan.shippingFeeRefund)
    }

    @Test
    fun 유료배송_변심은_발송_구간을_회수하지_않는다() {
        val o = deliveredOrder(line(2, 30_000))
        val id = o.itemIdOf(2)
        o.withReturn(id, ReturnReason.CHANGE_OF_MIND)

        val plan = o.plan(id)

        assertEquals(3_000, plan.returnFee)
        assertEquals(0, plan.outboundFeeRecovered)
        assertEquals(0, plan.shippingFeeRefund)
        assertEquals(27_000, plan.refundAmount)
    }

    @Test
    fun 판매자_주문이_비지_않으면_구매자_배송비는_돌려주지_않는다() {
        val o = deliveredOrder(line(2, 20_000, skuId = 21), line(2, 10_000, skuId = 22))
        val id = o.itemIdOf(2, 0)
        o.withReturn(id, ReturnReason.WRONG_DELIVERY)

        val plan = o.plan(id)

        assertEquals(0, plan.shippingFeeRefund)
        assertEquals(3_000, plan.sellerBorneFee)
        assertEquals(20_000, plan.refundAmount)
    }

    @Test
    fun paidAmount_4000_변심_무료배송은_환불_0_returnFee_3000_outbound_1000이다() {
        val o = deliveredOrder(line(1, 50_000, skuId = 11), line(1, 4_000, skuId = 12))
        val id = o.itemIdOf(1, 1)
        o.withReturn(id, ReturnReason.CHANGE_OF_MIND)

        val plan = o.plan(id)

        assertEquals(3_000, plan.returnFee)
        assertEquals(1_000, plan.outboundFeeRecovered)
        assertEquals(0, plan.refundAmount)
    }

    @Test
    fun 다른_항목이_CANCEL_REQUESTED면_RETURN_INVALID_STATE_TRANSITION() {
        val o = paidOrder(line(1, 30_000), line(2, 30_000))
        val so1 = o.sellerOrders.first { it.sellerId == 1L }.id!!
        o.ship(so1, "04", "T1", NOW)
        o.deliver(so1, NOW)
        val a = o.itemIdOf(1)
        o.withReturn(a, ReturnReason.CHANGE_OF_MIND)
        val b = o.itemIdOf(2)
        o.requestCancel(b, CancelActor.BUYER, "사유", o.planCancel(b, CancelActor.BUYER, POLICY))

        assertCode(ErrorCode.RETURN_INVALID_STATE_TRANSITION) { o.plan(a) }
    }

    @Test
    fun APPROVED가_아니면_RETURN_INVALID_STATE_TRANSITION() {
        val o = deliveredOrder(line(2, 30_000))
        val id = o.itemIdOf(2)
        o.withReturn(id, ReturnReason.CHANGE_OF_MIND, ReturnStatus.REQUESTED)

        assertCode(ErrorCode.RETURN_INVALID_STATE_TRANSITION) { o.plan(id) }
    }
}
