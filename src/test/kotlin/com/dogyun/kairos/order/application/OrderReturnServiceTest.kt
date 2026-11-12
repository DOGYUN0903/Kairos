package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.application.InlineTransactionRunner
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.OrderItemStatus
import com.dogyun.kairos.order.domain.OrderStatus
import com.dogyun.kairos.order.domain.PointUsePolicy
import com.dogyun.kairos.order.domain.SellerOrderStatus
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OrderReturnServiceTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 11, 10, 12, 0, 0)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)
    private val tx = InlineTransactionRunner()
    private val properties = OrderProperties()

    private val orderRepository = FakeOrderRepository()
    private val productReader = FakeProductReader()
    private val sellerReader = FakeSellerReader()
    private val stockManager = FakeStockManager()
    private val benefitReverter = FakeBenefitReverter(stockManager.events)
    private val benefitApplier = FakeBenefitApplier()
    private val paymentGateway = FakePaymentGateway()
    private val pointEarner = FakePointEarner()
    private val paymentService = OrderPaymentService(
        orderRepository, paymentGateway, stockManager, FakeBenefitReverter(), FakeCartItemRemover(), tx, clock,
    )
    private val orderService = OrderService(
        orderRepository, productReader, sellerReader, stockManager, paymentGateway, benefitApplier, paymentService, properties, tx, clock, Random(7),
    )
    private val shippingService = OrderShippingService(orderRepository, tx, clock, pointEarner)
    private val service = OrderReturnService(
        orderRepository, productReader, paymentGateway, stockManager, benefitReverter, pointEarner, properties, tx, clock,
    )

    private val address = ShippingAddressCommand("홍길동", "010-1234-5678", "06236", "서울 강남구 테헤란로 1", "101호", null)
    private val memberId = 7L

    @BeforeTest
    fun setUp() {
        sellerReader.put(1L)
        sellerReader.put(2L)
    }

    private fun sku(skuId: Long, sellerId: Long, price: Long, stock: Int = 10) {
        productReader.put(skuId, sellerId = sellerId, salePrice = price)
        stockManager.stock[skuId] = stock
    }

    /** items 는 (skuId, 수량). 결제 → 판매자 주문마다 배송 시작·완료까지 마친 주문. */
    private fun deliveredOrder(vararg items: Pair<Long, Int>, total: Long, points: Long = 0): Order {
        val placed = orderService.place(
            memberId, PlaceOrderCommand(items.map { OrderItemCommand(it.first, it.second) }, total, address, pointsToUse = points),
        )
        if (total > 0) {
            paymentGateway.nextConfirm = PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.DONE, total, total, emptyList()))
            paymentService.confirm(memberId, ConfirmPaymentCommand(placed.orderNo, "pay-${placed.orderId}", total))
        }
        val order = orderRepository.findById(placed.orderId)!!
        order.sellerOrders.forEach {
            shippingService.ship(it.sellerId, it.id!!, "04", "T-${it.id}")
            shippingService.deliver(it.sellerId, it.id!!)
        }
        return reload(order)
    }

    private fun refundDone(amount: Long, balance: Long) {
        paymentGateway.nextCancel = PgOutcome.Done(
            PgPaymentInfo(
                if (balance == 0L) PgPaymentStatus.CANCELED else PgPaymentStatus.PARTIAL_CANCELED,
                balance + amount, balance, listOf(PgCancelInfo(amount, "사유")),
            ),
        )
    }

    /** 반품 요청 → 판매자 승인. returnId. */
    private fun approved(order: Order, skuId: Long, reason: String = "CHANGE_OF_MIND"): Long {
        val item = order.itemOf(skuId)
        val returnId = service.request(memberId, order.id!!, item.id!!, RequestReturnCommand(reason, null)).returnId
        service.approveBySeller(order.sellerIdOf(skuId), returnId)
        return returnId
    }

    private fun Order.itemOf(skuId: Long) = allItems.first { it.skuId == skuId }
    private fun Order.sellerIdOf(skuId: Long) = sellerOrders.first { so -> so.items.any { it.skuId == skuId } }.sellerId
    private fun reload(order: Order) = orderRepository.findById(order.id!!)!!

    private fun assertError(code: ErrorCode, block: () -> Unit) {
        assertEquals(code, assertFailsWith<DomainException> { block() }.errorCode)
    }

    @Test
    fun 예시A_a2_수거_확인은_PG에_14000을_요청하고_shippingFeeRecovered가_3000이다() {
        sku(1L, 1L, 40_000)
        sku(2L, 1L, 20_000)
        val order = deliveredOrder(1L to 1, 2L to 1, total = 60_000)
        val a2 = order.itemOf(2L).id!!
        val returnId = approved(order, 2L)
        refundDone(14_000, 46_000)

        val result = service.receiveBySeller(1L, returnId)

        assertEquals(listOf(CancelCall("pay-${order.id}", 14_000, "orderItem:$a2", "refund-$a2-14000")), paymentGateway.cancelCalls)
        assertEquals("COMPLETED", result.status)
        assertEquals(14_000, result.refundAmount)
        assertEquals(3_000, result.returnFee)
        assertEquals(3_000, result.outboundFeeRecovered)
        val reloaded = reload(order)
        assertEquals(3_000, reloaded.shippingFeeRecovered)
        assertEquals(14_000, reloaded.refundedAmount)
        assertEquals(OrderItemStatus.RETURNED, reloaded.findItem(a2).status)
        assertEquals(SellerOrderStatus.DELIVERED, reloaded.sellerOrders.single().status)
        assertEquals("PARTIAL_CANCELED", paymentGateway.get(order.id!!).status)
        assertTrue(pointEarner.calls.isEmpty())
    }

    @Test
    fun 예시A_a1_이어_수거_확인은_37000이고_판매자_주문이_CONFIRMED_주문이_COMPLETED_적립_인자_0이다() {
        sku(1L, 1L, 40_000)
        sku(2L, 1L, 20_000)
        val order = deliveredOrder(1L to 1, 2L to 1, total = 60_000)
        refundDone(14_000, 46_000)
        service.receiveBySeller(1L, approved(order, 2L))
        val a1Return = approved(reload(order), 1L)
        refundDone(37_000, 9_000)

        val result = service.receiveBySeller(1L, a1Return)

        assertEquals(37_000, result.refundAmount)
        assertEquals(0, result.outboundFeeRecovered)
        val reloaded = reload(order)
        val so = reloaded.sellerOrders.single()
        assertEquals(SellerOrderStatus.CONFIRMED, so.status)
        assertEquals(now, so.confirmedAt)
        assertTrue(so.allItemsReturned)
        assertEquals(OrderStatus.COMPLETED, reloaded.status)
        assertEquals(51_000, reloaded.refundedAmount)
        assertEquals(3_000, reloaded.shippingFeeRecovered)
        assertEquals(listOf(FakePointEarner.Earn(memberId, so.id!!, 0)), pointEarner.calls)
        assertEquals("PARTIAL_CANCELED", paymentGateway.get(order.id!!).status)
        assertEquals(51_000, paymentGateway.get(order.id!!).refundedAmount)
    }

    @Test
    fun 예시B_불량_수거_확인은_33000이고_재고는_그대로_판매량만_준다() {
        sku(3L, 2L, 30_000)
        val order = deliveredOrder(3L to 1, total = 33_000)
        val stockBefore = stockManager.stock[3L]
        val salesBefore = stockManager.sales[103L] ?: 0
        val returnId = approved(order, 3L, "DEFECTIVE")
        refundDone(33_000, 0)

        val result = service.receiveBySeller(2L, returnId)

        assertEquals(33_000, result.refundAmount)
        assertEquals(3_000, result.shippingFeeRefund)
        assertEquals(3_000, result.sellerBorneFee)
        assertEquals(0, result.returnFee)
        assertEquals(stockBefore, stockManager.stock[3L])
        assertTrue(stockManager.restoreCalls.isEmpty())
        assertEquals(salesBefore - 1, stockManager.sales[103L])
        assertEquals("CANCELED", paymentGateway.get(order.id!!).status)
        assertEquals(0, reload(order).remainingBuyerShippingFee)
        assertEquals(SellerOrderStatus.CONFIRMED, reload(order).sellerOrders.single().status)
    }

    @Test
    fun 변심_반품_완료는_재고_plus수량_판매량_minus수량이다() {
        sku(1L, 1L, 30_000)
        sku(2L, 1L, 30_000)
        val order = deliveredOrder(1L to 2, 2L to 1, total = 90_000)
        val stockBefore = stockManager.stock[1L]!!
        val salesBefore = stockManager.sales[101L] ?: 0
        val returnId = approved(order, 1L)
        refundDone(54_000, 36_000)

        val result = service.receiveBySeller(1L, returnId)

        assertEquals(54_000, result.refundAmount) // 60,000 - 3,000 - 3,000
        assertEquals(stockBefore + 2, stockManager.stock[1L])
        assertEquals(salesBefore - 2, stockManager.sales[101L])
    }

    @Test
    fun revertItem은_재고_복구보다_먼저_불리고_마지막_항목이면_cartCouponId를_넘긴다() {
        sku(1L, 1L, 30_000)
        sku(2L, 1L, 30_000)
        benefitApplier.cart(500L, discountType = "FIXED", value = 2_000, max = null)
        val placed = orderService.place(
            memberId, PlaceOrderCommand(listOf(OrderItemCommand(1L, 1), OrderItemCommand(2L, 1)), 58_000, address, cartCouponId = 500L),
        )
        paymentGateway.nextConfirm = PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.DONE, 58_000, 58_000, emptyList()))
        paymentService.confirm(memberId, ConfirmPaymentCommand(placed.orderNo, "pay-${placed.orderId}", 58_000))
        var order = orderRepository.findById(placed.orderId)!!
        val so = order.sellerOrders.single()
        shippingService.ship(1L, so.id!!, "04", "T-1")
        shippingService.deliver(1L, so.id!!)
        order = reload(order)

        paymentGateway.nextCancel = PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.PARTIAL_CANCELED, 58_000, 1, emptyList()))
        service.receiveBySeller(1L, approved(order, 1L))
        assertEquals(null, benefitReverter.revertItemCalls.last().cartCouponId)
        service.receiveBySeller(1L, approved(reload(order), 2L))

        assertEquals(500L, benefitReverter.revertItemCalls.last().cartCouponId)
        assertEquals(listOf("revertItem", "restore", "revertItem", "restore"), stockManager.events)
    }

    @Test
    fun PG_실패면_RETURN_APPROVED로_돌아가고_포인트_쿠폰_재고_판매량_변화가_없다() {
        sku(1L, 1L, 30_000)
        sku(2L, 1L, 30_000)
        val order = deliveredOrder(1L to 1, 2L to 1, total = 60_000)
        val returnId = approved(order, 1L)
        val stock = stockManager.stock.toMap()
        val sales = stockManager.sales.toMap()
        paymentGateway.nextCancel = PgOutcome.Failed("REJECT", "거절")

        assertError(ErrorCode.PAYMENT_CANCEL_FAILED) { service.receiveBySeller(1L, returnId) }

        val reloaded = reload(order)
        val item = reloaded.itemOf(1L)
        assertEquals(OrderItemStatus.RETURN_APPROVED, item.status)
        assertEquals(0, item.returnRequest!!.refundAmount)
        assertEquals(0, item.returnRequest!!.outboundFeeRecovered)
        assertEquals(0, reloaded.refundedAmount)
        assertEquals(0, reloaded.shippingFeeRecovered)
        assertTrue(benefitReverter.revertItemCalls.isEmpty())
        assertEquals(stock, stockManager.stock)
        assertEquals(sales, stockManager.sales)
        assertTrue(pointEarner.calls.isEmpty())
    }

    @Test
    fun PG_실패_되돌림_뒤_다시_수거_확인하면_같은_환불액이다() {
        sku(1L, 1L, 30_000)
        sku(2L, 1L, 30_000)
        val order = deliveredOrder(1L to 1, 2L to 1, total = 60_000)
        val itemId = order.itemOf(1L).id!!
        val returnId = approved(order, 1L)
        paymentGateway.nextCancel = PgOutcome.Failed("REJECT", "거절")
        assertError(ErrorCode.PAYMENT_CANCEL_FAILED) { service.receiveBySeller(1L, returnId) }
        refundDone(24_000, 36_000)

        val result = service.receiveBySeller(1L, returnId)

        assertEquals(24_000, result.refundAmount)
        assertEquals(3_000, result.outboundFeeRecovered)
        assertEquals(
            listOf("refund-$itemId-24000", "refund-$itemId-24000"),
            paymentGateway.cancelCalls.map { it.idempotencyKey },
        )
    }

    @Test
    fun PG_불확실이면_RETURN_REFUNDING으로_남는다() {
        sku(1L, 1L, 30_000)
        sku(2L, 1L, 30_000)
        val order = deliveredOrder(1L to 1, 2L to 1, total = 60_000)
        val returnId = approved(order, 1L)
        paymentGateway.nextCancel = PgOutcome.Unknown

        val result = service.receiveBySeller(1L, returnId)

        assertEquals("REFUNDING", result.status)
        assertEquals(24_000, result.refundAmount)
        assertEquals(OrderItemStatus.RETURN_REFUNDING, reload(order).itemOf(1L).status)
        assertEquals(0, reload(order).refundedAmount)
        assertTrue(benefitReverter.revertItemCalls.isEmpty())
    }

    @Test
    fun 환불_0원이면_PG_없이_RETURNED다() {
        // 무료배송 주문의 5,000원 항목 변심 반품: 반품비 3,000 + 발송 구간 2,000(남은 현금까지만) → 환불 0.
        sku(1L, 1L, 55_000)
        sku(2L, 1L, 5_000)
        val order = deliveredOrder(1L to 1, 2L to 1, total = 60_000)
        val returnId = approved(order, 2L)

        val result = service.receiveBySeller(1L, returnId)

        assertEquals("COMPLETED", result.status)
        assertEquals(0, result.refundAmount)
        assertEquals(2_000, result.outboundFeeRecovered)
        assertTrue(paymentGateway.cancelCalls.isEmpty())
        assertEquals(0, paymentGateway.get(order.id!!).refundedAmount)
        assertEquals(OrderItemStatus.RETURNED, reload(order).itemOf(2L).status)
    }

    @Test
    fun paymentKey_없는_0원_주문은_PG_없이_RETURNED다() {
        sku(1L, 1L, 60_000)
        benefitApplier.balance = 60_000
        benefitApplier.policy = PointUsePolicy(minUsableBalance = 0, useUnit = 10, maxUseRateBp = 10_000)
        val order = deliveredOrder(1L to 1, total = 0, points = 60_000)
        assertEquals(null, paymentGateway.get(order.id!!).paymentKey)
        val returnId = approved(order, 1L, "DEFECTIVE")

        val result = service.receiveBySeller(1L, returnId)

        assertEquals("COMPLETED", result.status)
        assertEquals(0, result.refundAmount)
        assertEquals(60_000, result.pointsRefund)
        assertTrue(paymentGateway.cancelCalls.isEmpty())
        assertEquals(60_000, benefitReverter.revertItemCalls.single().pointsRefund)
        assertEquals(60_000, reload(order).pointsRefunded)
    }

    @Test
    fun 수거_확인_두_번은_두_번째가_현재_상태를_돌려주고_PG는_한_번이다() {
        sku(1L, 1L, 30_000)
        sku(2L, 1L, 30_000)
        val order = deliveredOrder(1L to 1, 2L to 1, total = 60_000)
        val returnId = approved(order, 1L)
        refundDone(24_000, 36_000)

        service.receiveBySeller(1L, returnId)
        val second = service.receiveBySeller(1L, returnId)

        assertEquals("COMPLETED", second.status)
        assertEquals(1, paymentGateway.cancelCalls.size)
        assertEquals(1, benefitReverter.revertItemCalls.size)
        assertEquals(24_000, reload(order).refundedAmount)
    }

    @Test
    fun 판매자가_남의_반품을_승인하면_RETURN_NOT_FOUND() {
        sku(1L, 1L, 30_000)
        val order = deliveredOrder(1L to 1, total = 33_000)
        val returnId = service.request(memberId, order.id!!, order.itemOf(1L).id!!, RequestReturnCommand("DEFECTIVE", null)).returnId

        assertError(ErrorCode.RETURN_NOT_FOUND) { service.approveBySeller(2L, returnId) }
        assertError(ErrorCode.RETURN_NOT_FOUND) { service.receiveBySeller(2L, returnId) }
        assertEquals(OrderItemStatus.RETURN_REQUESTED, reload(order).itemOf(1L).status)
    }

    @Test
    fun 승인_전_수거_확인은_RETURN_INVALID_STATE_TRANSITION() {
        sku(1L, 1L, 30_000)
        val order = deliveredOrder(1L to 1, total = 33_000)
        val returnId = service.request(memberId, order.id!!, order.itemOf(1L).id!!, RequestReturnCommand("DEFECTIVE", null)).returnId

        assertError(ErrorCode.RETURN_INVALID_STATE_TRANSITION) { service.receiveBySeller(1L, returnId) }
        assertTrue(paymentGateway.cancelCalls.isEmpty())
    }

    @Test
    fun 사유가_enum이_아니면_RETURN_INVALID이고_남의_주문은_ORDER_NOT_FOUND() {
        sku(1L, 1L, 30_000)
        val order = deliveredOrder(1L to 1, total = 33_000)
        val itemId = order.itemOf(1L).id!!

        assertError(ErrorCode.RETURN_INVALID) { service.request(memberId, order.id!!, itemId, RequestReturnCommand("BORING", null)) }
        assertError(ErrorCode.ORDER_NOT_FOUND) { service.request(99L, order.id!!, itemId, RequestReturnCommand("DEFECTIVE", null)) }
    }

    @Test
    fun 철회_뒤_재요청은_같은_returnId이고_철회는_멱등이다() {
        sku(1L, 1L, 30_000)
        val order = deliveredOrder(1L to 1, total = 33_000)
        val itemId = order.itemOf(1L).id!!
        val returnId = service.request(memberId, order.id!!, itemId, RequestReturnCommand("DEFECTIVE", null)).returnId

        assertError(ErrorCode.RETURN_NOT_FOUND) { service.withdraw(99L, returnId) }
        assertEquals("WITHDRAWN", service.withdraw(memberId, returnId).status)
        assertEquals("WITHDRAWN", service.withdraw(memberId, returnId).status)
        assertEquals(OrderItemStatus.ORDERED, reload(order).itemOf(1L).status)
        assertEquals(returnId, service.request(memberId, order.id!!, itemId, RequestReturnCommand("CHANGE_OF_MIND", null)).returnId)
    }

    @Test
    fun 관리자_취소와_자동_승인() {
        sku(1L, 1L, 30_000)
        val order = deliveredOrder(1L to 1, total = 33_000)
        val itemId = order.itemOf(1L).id!!
        val returnId = service.request(memberId, order.id!!, itemId, RequestReturnCommand("DEFECTIVE", null)).returnId

        assertTrue(service.approveBySystem(order.id!!, returnId))
        assertEquals(false, service.approveBySystem(order.id!!, returnId))
        assertEquals("CANCELED_BY_ADMIN", service.cancelByAdmin(returnId).status)
        assertEquals("CANCELED_BY_ADMIN", service.cancelByAdmin(returnId).status)
        assertEquals(OrderItemStatus.ORDERED, reload(order).itemOf(1L).status)
    }

    @Test
    fun 현금_환불이_있는데_paymentKey가_없으면_RETURN_REFUNDING으로_남고_되돌림이_없다() {
        sku(1L, 1L, 30_000)
        sku(2L, 1L, 30_000)
        val order = deliveredOrder(1L to 1, 2L to 1, total = 60_000)
        val returnId = approved(order, 1L)
        paymentGateway.payments[order.id!!] = paymentGateway.get(order.id!!).copy(paymentKey = null)
        val stock = stockManager.stock.toMap()
        val sales = stockManager.sales.toMap()

        val result = service.receiveBySeller(1L, returnId)

        assertEquals("REFUNDING", result.status)
        assertEquals(OrderItemStatus.RETURN_REFUNDING, reload(order).itemOf(1L).status)
        assertEquals(0, reload(order).refundedAmount)
        assertEquals(0, paymentGateway.get(order.id!!).refundedAmount)
        assertTrue(paymentGateway.cancelCalls.isEmpty())
        assertTrue(benefitReverter.revertItemCalls.isEmpty())
        assertTrue(stockManager.restoreCalls.isEmpty())
        assertEquals(stock, stockManager.stock)
        assertEquals(sales, stockManager.sales)
    }
}
