package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.application.InlineTransactionRunner
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.OrderItemStatus
import com.dogyun.kairos.order.domain.OrderStatus
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OrderCancelServiceTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 11, 10, 12, 0, 0)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)

    private val orderRepository = FakeOrderRepository()
    private val productReader = FakeProductReader()
    private val sellerReader = FakeSellerReader()
    private val stockManager = FakeStockManager()
    private val benefitReverter = FakeBenefitReverter(stockManager.events)
    private val benefitApplier = FakeBenefitApplier()
    private val paymentGateway = FakePaymentGateway()
    private val orderService = OrderService(
        orderRepository, productReader, sellerReader, stockManager, paymentGateway, benefitApplier,
        OrderPaymentService(orderRepository, paymentGateway, stockManager, FakeBenefitReverter(), FakeCartItemRemover(), InlineTransactionRunner(), clock),
        OrderProperties(), InlineTransactionRunner(), clock, Random(7),
    )
    private val paymentService = OrderPaymentService(
        orderRepository, paymentGateway, stockManager, benefitReverter, FakeCartItemRemover(), InlineTransactionRunner(), clock,
    )
    private val service = OrderCancelService(
        orderRepository, paymentGateway, stockManager, benefitReverter, OrderProperties(), InlineTransactionRunner(), clock,
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

    /** items 는 (skuId, 수량). 주문을 만들고 승인해 PAID 로 만든다. */
    private fun paidOrder(vararg items: Pair<Long, Int>, total: Long): Order {
        val placed = orderService.place(memberId, PlaceOrderCommand(items.map { OrderItemCommand(it.first, it.second) }, total, address))
        paymentGateway.nextConfirm = PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.DONE, total, total, emptyList()))
        paymentService.confirm(memberId, ConfirmPaymentCommand(placed.orderNo, "pay-${placed.orderId}", total))
        return orderRepository.findById(placed.orderId)!!
    }

    private fun cancelDone(amount: Long, balance: Long) {
        paymentGateway.nextCancel = PgOutcome.Done(
            PgPaymentInfo(
                if (balance == 0L) PgPaymentStatus.CANCELED else PgPaymentStatus.PARTIAL_CANCELED,
                balance + amount, balance, listOf(PgCancelInfo(amount, "사유")),
            ),
        )
    }

    private fun Order.itemOf(skuId: Long) = allItems.first { it.skuId == skuId }
    private fun Order.sellerOrderIdOf(sellerId: Long) = sellerOrders.first { it.sellerId == sellerId }.id!!
    private fun reload(order: Order) = orderRepository.findById(order.id!!)!!

    private fun assertError(code: ErrorCode, block: () -> Unit) {
        assertEquals(code, assertFailsWith<DomainException> { block() }.errorCode)
    }

    /** 30,000 + 30,000 (판매자 1, 2), 무료배송. */
    private fun freeShippingOrder(): Order {
        sku(10L, 1L, 30_000)
        sku(20L, 2L, 30_000)
        return paidOrder(10L to 1, 20L to 1, total = 60_000)
    }

    @Test
    fun 구매자_취소_성공_시_항목_CANCELED_환불_기록_재고_복구_판매량_감소() {
        sku(10L, 1L, 9_000, stock = 5)
        sku(11L, 1L, 9_000, stock = 5)
        val order = paidOrder(10L to 2, 11L to 1, total = 30_000)
        val item = order.itemOf(10L)
        cancelDone(18_000, 12_000)

        val result = service.cancelByBuyer(memberId, order.id!!, item.id!!, "단순 변심")

        assertEquals(CancelItemResult(item.id, "CANCELED", 18_000, 0, 0), result)
        val saved = reload(order)
        assertEquals(OrderItemStatus.CANCELED, saved.findItem(item.id).status)
        assertEquals(18_000, saved.refundedAmount)
        assertEquals(OrderStatus.PAID, saved.status)
        assertEquals(18_000, paymentGateway.get(order.id).refundedAmount)
        assertEquals("PARTIAL_CANCELED", paymentGateway.get(order.id).status)
        assertEquals(5, stockManager.stock[10L])
        assertEquals(0, stockManager.sales[110L])
        assertEquals(listOf(CancelCall("pay-${order.id}", 18_000, "orderItem:${item.id}", "refund-${item.id}-18000")), paymentGateway.cancelCalls)
        assertEquals("단순 변심", saved.findItem(item.id).cancelReason)
    }

    @Test
    fun 스펙_예시_B3만_C3만에서_C_취소_환불은_27000이고_PG에_27000을_요청한다() {
        val order = freeShippingOrder()
        val c = order.itemOf(20L)
        cancelDone(27_000, 33_000)

        val result = service.cancelByBuyer(memberId, order.id!!, c.id!!, "변심")

        assertEquals(CancelItemResult(c.id, "CANCELED", 27_000, 3_000, 0), result)
        assertEquals(listOf(27_000L), paymentGateway.cancelCalls.map { it.amount })
        val saved = reload(order)
        assertEquals(3_000, saved.shippingFeeRecovered)
        assertEquals(27_000, saved.refundedAmount)
        // 남은 B 주문은 그대로 PAID 이고 배송비 3,000 은 플랫폼 몫이다.
        assertEquals(OrderStatus.PAID, saved.status)
    }

    @Test
    fun 판매자_취소는_전액_환불하고_차감하지_않는다() {
        val order = freeShippingOrder()
        val c = order.itemOf(20L)
        cancelDone(30_000, 30_000)

        val result = service.cancelBySeller(2L, order.sellerOrderIdOf(2L), c.id!!, "재고 착오")

        assertEquals(CancelItemResult(c.id, "CANCELED", 30_000, 0, 0), result)
        assertEquals(listOf(30_000L), paymentGateway.cancelCalls.map { it.amount })
        assertEquals(0, reload(order).shippingFeeRecovered)
        assertEquals("재고 착오", reload(order).findItem(c.id).cancelReason)
    }

    @Test
    fun 관리자_취소도_전액_환불한다() {
        val order = freeShippingOrder()
        val c = order.itemOf(20L)
        cancelDone(30_000, 30_000)

        val result = service.cancelByAdmin(order.id!!, c.id!!, "운영 정책")

        assertEquals(CancelItemResult(c.id, "CANCELED", 30_000, 0, 0), result)
    }

    @Test
    fun 환불_0원_취소는_PG를_호출하지_않고_바로_확정된다() {
        sku(10L, 1L, 47_000)
        sku(20L, 2L, 3_000)
        val order = paidOrder(10L to 1, 20L to 1, total = 50_000)
        val small = order.itemOf(20L)

        val result = service.cancelByBuyer(memberId, order.id!!, small.id!!, "변심")

        // 3,000 짜리를 취소하면 남은 금액이 47,000 이라 배송비 3,000 이 차감되어 환불이 0 이다.
        assertEquals(CancelItemResult(small.id, "CANCELED", 0, 3_000, 0), result)
        assertTrue(paymentGateway.cancelCalls.isEmpty())
        val saved = reload(order)
        assertEquals(OrderItemStatus.CANCELED, saved.findItem(small.id).status)
        assertEquals(3_000, saved.shippingFeeRecovered)
        assertEquals(0, saved.refundedAmount)
        assertEquals(0, paymentGateway.get(order.id).refundedAmount)
        assertEquals(10, stockManager.stock[20L])
        assertEquals(0, stockManager.sales[120L])
    }

    @Test
    fun PG_결과가_불확실하면_CANCEL_REQUESTED로_남고_재고는_그대로다() {
        sku(10L, 1L, 9_000, stock = 5)
        val order = paidOrder(10L to 2, total = 21_000)
        val item = order.itemOf(10L)
        paymentGateway.nextCancel = PgOutcome.Unknown

        val result = service.cancelByBuyer(memberId, order.id!!, item.id!!, "변심")

        assertEquals("CANCEL_REQUESTED", result.itemStatus)
        assertEquals(OrderItemStatus.CANCEL_REQUESTED, reload(order).findItem(item.id).status)
        assertEquals(3, stockManager.stock[10L])
        assertEquals(2, stockManager.sales[110L])
        assertEquals(0, paymentGateway.get(order.id).refundedAmount)
        assertEquals(0, reload(order).refundedAmount)
    }

    @Test
    fun PG가_확정_실패하면_ORDERED로_돌아가고_PAYMENT_CANCEL_FAILED() {
        sku(10L, 1L, 9_000, stock = 5)
        val order = paidOrder(10L to 2, total = 21_000)
        val item = order.itemOf(10L)
        paymentGateway.nextCancel = PgOutcome.Failed("REJECT_CANCEL", "거절")

        assertError(ErrorCode.PAYMENT_CANCEL_FAILED) { service.cancelByBuyer(memberId, order.id!!, item.id!!, "변심") }

        val saved = reload(order).findItem(item.id!!)
        assertEquals(OrderItemStatus.ORDERED, saved.status)
        assertEquals(0, saved.refundAmount)
        assertEquals(3, stockManager.stock[10L])
        assertEquals(0, paymentGateway.get(order.id!!).refundedAmount)
    }

    @Test
    fun 같은_항목_재요청은_PG_없이_현재_상태를_돌려준다() {
        sku(10L, 1L, 9_000, stock = 5)
        val order = paidOrder(10L to 2, total = 21_000)
        val item = order.itemOf(10L)
        paymentGateway.nextCancel = PgOutcome.Unknown
        val first = service.cancelByBuyer(memberId, order.id!!, item.id!!, "변심")

        val again = service.cancelByBuyer(memberId, order.id, item.id, "변심")

        assertEquals(first, again)
        assertEquals(1, paymentGateway.cancelCalls.size)

        // 확정된 항목도 마찬가지다.
        service.finishCancel(order.id, item.id, PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.CANCELED, 21_000, 0, emptyList())))
        val afterConfirm = service.cancelByBuyer(memberId, order.id, item.id, "변심")
        assertEquals("CANCELED", afterConfirm.itemStatus)
        assertEquals(21_000, afterConfirm.refundAmount)
        assertEquals(1, paymentGateway.cancelCalls.size)
        assertEquals(21_000, paymentGateway.get(order.id).refundedAmount)
    }

    @Test
    fun 다른_회원의_주문_취소는_ORDER_NOT_FOUND_다른_판매자는_SELLER_ORDER_NOT_FOUND() {
        val order = freeShippingOrder()
        val c = order.itemOf(20L)

        assertError(ErrorCode.ORDER_NOT_FOUND) { service.cancelByBuyer(999L, order.id!!, c.id!!, "변심") }
        assertError(ErrorCode.ORDER_NOT_FOUND) { service.cancelByBuyer(memberId, 12345L, c.id!!, "변심") }
        assertError(ErrorCode.SELLER_ORDER_NOT_FOUND) { service.cancelBySeller(1L, order.sellerOrderIdOf(2L), c.id!!, "x") }
        assertError(ErrorCode.SELLER_ORDER_NOT_FOUND) { service.cancelBySeller(2L, 98765L, c.id!!, "x") }
        // 내 판매자 주문이어도 다른 판매자 주문의 항목은 찾을 수 없다.
        assertError(ErrorCode.ORDER_ITEM_NOT_FOUND) {
            service.cancelBySeller(1L, order.sellerOrderIdOf(1L), c.id!!, "x")
        }
        assertError(ErrorCode.ORDER_ITEM_NOT_FOUND) { service.cancelByBuyer(memberId, order.id!!, 55555L, "x") }
        assertTrue(paymentGateway.cancelCalls.isEmpty())
    }

    @Test
    fun 단종된_SKU_항목도_재고를_복구한다() {
        sku(10L, 1L, 9_000, stock = 5)
        val order = paidOrder(10L to 2, total = 21_000)
        val item = order.itemOf(10L)
        // 주문 이후 SKU 가 단종되어도 취소 흐름은 SKU 상태를 보지 않고 항목의 skuId·수량으로 복구한다.
        productReader.skus.remove(10L)
        cancelDone(21_000, 0)

        service.cancelByBuyer(memberId, order.id!!, item.id!!, "변심")

        assertEquals(listOf(SkuQuantity(10L, 2)), stockManager.restoreCalls)
        assertEquals(5, stockManager.stock[10L])
    }

    @Test
    fun 항목이_이미_CANCELED면_finishCancel은_no_op이다() {
        sku(10L, 1L, 9_000, stock = 5)
        val order = paidOrder(10L to 2, total = 21_000)
        val item = order.itemOf(10L)
        cancelDone(21_000, 0)
        service.cancelByBuyer(memberId, order.id!!, item.id!!, "변심")

        val finish = service.finishCancel(order.id, item.id, PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.CANCELED, 21_000, 0, emptyList())))

        assertEquals(CancelFinish.CANCELED, finish)
        assertEquals(21_000, paymentGateway.get(order.id).refundedAmount)
        assertEquals(1, stockManager.restoreCalls.size)
        assertEquals(0, stockManager.sales[110L])
    }

    @Test
    fun 무료배송_주문_마지막_항목_취소는_차감했던_배송비를_돌려주고_결제가_CANCELED다() {
        val order = freeShippingOrder()
        cancelDone(27_000, 33_000)
        service.cancelByBuyer(memberId, order.id!!, order.itemOf(20L).id!!, "변심")

        cancelDone(33_000, 0)
        val last = service.cancelByBuyer(memberId, order.id, order.itemOf(10L).id!!, "변심")

        assertEquals(CancelItemResult(order.itemOf(10L).id!!, "CANCELED", 33_000, 0, 0), last)
        assertEquals(listOf(27_000L, 33_000L), paymentGateway.cancelCalls.map { it.amount })
        val saved = reload(order)
        assertEquals(OrderStatus.CANCELED, saved.status)
        assertEquals(60_000, saved.refundedAmount)
        assertEquals(0, saved.shippingFeeRecovered)
        assertEquals("CANCELED", paymentGateway.get(order.id).status)
        assertEquals(60_000, paymentGateway.get(order.id).refundedAmount)
    }

    @Test
    fun 구매자가_배송비를_낸_주문은_판매자_주문이_비면_배송비도_환불한다() {
        sku(10L, 1L, 20_000)
        sku(20L, 2L, 10_000)
        val order = paidOrder(10L to 1, 20L to 1, total = 36_000)
        cancelDone(13_000, 23_000)
        val first = service.cancelByBuyer(memberId, order.id!!, order.itemOf(20L).id!!, "변심")
        cancelDone(23_000, 0)
        val second = service.cancelByBuyer(memberId, order.id, order.itemOf(10L).id!!, "변심")

        assertEquals(13_000, first.refundAmount)
        assertEquals(23_000, second.refundAmount)
        assertEquals("CANCELED", paymentGateway.get(order.id).status)
    }

    @Test
    fun 다른_항목의_취소가_확인_중이면_ORDER_ITEM_NOT_CANCELABLE이다() {
        val order = freeShippingOrder()
        paymentGateway.nextCancel = PgOutcome.Unknown
        service.cancelByBuyer(memberId, order.id!!, order.itemOf(20L).id!!, "변심")

        assertError(ErrorCode.ORDER_ITEM_NOT_CANCELABLE) {
            service.cancelByBuyer(memberId, order.id, order.itemOf(10L).id!!, "변심")
        }
        assertEquals(1, paymentGateway.cancelCalls.size)
    }

    @Test
    fun 사유가_비었거나_500자를_넘으면_ORDER_INVALID이고_아무것도_하지_않는다() {
        val order = freeShippingOrder()
        val item = order.itemOf(20L).id!!

        assertError(ErrorCode.ORDER_INVALID) { service.cancelByBuyer(memberId, order.id!!, item, "") }
        assertError(ErrorCode.ORDER_INVALID) { service.cancelByBuyer(memberId, order.id!!, item, "  ") }
        assertError(ErrorCode.ORDER_INVALID) { service.cancelByBuyer(memberId, order.id!!, item, "가".repeat(501)) }
        assertTrue(paymentGateway.cancelCalls.isEmpty())
        assertEquals(OrderItemStatus.ORDERED, reload(order).findItem(item).status)
    }

    /**
     * 스펙 6장 예시 주문: b1(sku 31, 판매자 1, 30,000×1, 플랫폼 쿠폰 501), c1(sku 32, 판매자 2, 20,000×2, 판매자 쿠폰 502),
     * 장바구니 쿠폰 503, 포인트 10,000. 총 49,000원, paidAmount 21,438/27,562.
     */
    private fun spec6Order(): Order {
        sku(31L, 1L, 30_000)
        sku(32L, 2L, 20_000)
        benefitApplier.product(501L, issuer = "PLATFORM", value = 2_000)
        benefitApplier.product(502L, issuer = "SELLER", target = "SELLER", sellerId = 2L, discountType = "RATE", value = 1_000, max = 5_000)
        benefitApplier.cart(503L, value = 1_000, max = 5_000, min = 50_000)
        benefitApplier.balance = 20_000
        val placed = orderService.place(
            memberId,
            PlaceOrderCommand(
                listOf(OrderItemCommand(31L, 1), OrderItemCommand(32L, 2)), 49_000, address,
                listOf(ProductCouponCommand(31L, 501L), ProductCouponCommand(32L, 502L)), 503L, 10_000,
            ),
        )
        paymentGateway.nextConfirm = PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.DONE, 49_000, 49_000, emptyList()))
        paymentService.confirm(memberId, ConfirmPaymentCommand(placed.orderNo, "pay-${placed.orderId}", 49_000))
        return orderRepository.findById(placed.orderId)!!
    }

    @Test
    fun 스펙_예시_c1_취소는_PG에_24562를_요청하고_revertItem에_5625와_판매자_쿠폰을_넘긴다() {
        val order = spec6Order()
        val c1 = order.itemOf(32L)
        cancelDone(24_562, 24_438)

        val result = service.cancelByBuyer(memberId, order.id!!, c1.id!!, "변심")

        assertEquals(CancelItemResult(c1.id, "CANCELED", 24_562, 3_000, 5_625), result)
        assertEquals(24_562, paymentGateway.cancelCalls.single().amount)
        assertEquals(listOf(FakeBenefitReverter.RevertItem(order.id, memberId, c1.id, 5_625, 502L, null)), benefitReverter.revertItemCalls)
        assertEquals(listOf("revertItem", "restore"), stockManager.events)
    }

    @Test
    fun 마지막_항목_취소는_cartCouponId를_넘긴다() {
        val order = spec6Order()
        cancelDone(24_562, 24_438)
        service.cancelByBuyer(memberId, order.id!!, order.itemOf(32L).id!!, "변심")
        cancelDone(24_438, 0)

        val result = service.cancelByBuyer(memberId, order.id, order.itemOf(31L).id!!, "변심")

        assertEquals(24_438, result.refundAmount)
        assertEquals(4_375, result.pointsRefund)
        assertEquals(
            FakeBenefitReverter.RevertItem(order.id, memberId, order.itemOf(31L).id!!, 4_375, 501L, 503L),
            benefitReverter.revertItemCalls.last(),
        )
        assertEquals("CANCELED", paymentGateway.get(order.id).status)
    }

    @Test
    fun 중간_항목_취소는_cartCouponId가_null이다() {
        val order = spec6Order()
        cancelDone(24_562, 24_438)

        service.cancelByBuyer(memberId, order.id!!, order.itemOf(32L).id!!, "변심")

        assertEquals(null, benefitReverter.revertItemCalls.single().cartCouponId)
    }

    @Test
    fun 판매자_관리자_취소에서도_revertItem_인자는_구매자와_같다() {
        val bySeller = spec6Order()
        cancelDone(27_562, 21_438)
        service.cancelBySeller(2L, bySeller.sellerOrderIdOf(2L), bySeller.itemOf(32L).id!!, "재고 착오")
        benefitApplier.used.clear() // Fake 는 쿠폰을 되돌리지 않는다. 같은 쿠폰으로 두 번째 주문을 만든다.
        val byAdmin = spec6Order()
        cancelDone(27_562, 21_438)
        service.cancelByAdmin(byAdmin.id!!, byAdmin.itemOf(32L).id!!, "운영 정책")

        assertEquals(
            listOf(
                FakeBenefitReverter.RevertItem(bySeller.id!!, memberId, bySeller.itemOf(32L).id!!, 5_625, 502L, null),
                FakeBenefitReverter.RevertItem(byAdmin.id, memberId, byAdmin.itemOf(32L).id!!, 5_625, 502L, null),
            ),
            benefitReverter.revertItemCalls,
        )
    }

    @Test
    fun PG가_실패하면_revertItem은_불리지_않는다() {
        val order = spec6Order()
        paymentGateway.nextCancel = PgOutcome.Failed("REJECT_CANCEL", "거절")

        assertError(ErrorCode.PAYMENT_CANCEL_FAILED) { service.cancelByBuyer(memberId, order.id!!, order.itemOf(32L).id!!, "변심") }

        assertEquals(emptyList(), benefitReverter.revertItemCalls)
    }

    @Test
    fun 현금_환불_0원_항목은_PG_없이_닫히고_revertItem이_불린다() {
        sku(10L, 1L, 30_000)
        sku(40L, 1L, 2_000)
        benefitApplier.product(601L, value = 2_000)
        val items = listOf(OrderItemCommand(10L, 1), OrderItemCommand(40L, 1))
        val coupons = listOf(ProductCouponCommand(40L, 601L))
        val total = orderService.quote(memberId, QuoteOrderCommand(items, coupons, null, 0)).totalAmount
        val placed = orderService.place(memberId, PlaceOrderCommand(items, total, address, coupons))
        paymentGateway.nextConfirm = PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.DONE, total, total, emptyList()))
        paymentService.confirm(memberId, ConfirmPaymentCommand(placed.orderNo, "pay-${placed.orderId}", total))
        val order = orderRepository.findById(placed.orderId)!!
        val free = order.itemOf(40L)

        val result = service.cancelByBuyer(memberId, order.id!!, free.id!!, "변심")

        assertEquals("CANCELED", result.itemStatus)
        assertEquals(0, result.refundAmount)
        assertEquals(emptyList(), paymentGateway.cancelCalls)
        assertEquals(listOf(FakeBenefitReverter.RevertItem(order.id, memberId, free.id, 0, 601L, null)), benefitReverter.revertItemCalls)
    }

    @Test
    fun `0원_결제_주문의_항목_취소는_paymentKey_없이_PG_없이_닫힌다`() {
        sku(10L, 1L, 60_000)
        benefitApplier.product(701L, value = 60_000)
        val placed = orderService.place(
            memberId, PlaceOrderCommand(listOf(OrderItemCommand(10L, 1)), 0, address, listOf(ProductCouponCommand(10L, 701L))),
        )
        val order = orderRepository.findById(placed.orderId)!!
        assertEquals(OrderStatus.PAID, order.status)
        assertEquals(null, paymentGateway.get(order.id!!).paymentKey)
        val item = order.itemOf(10L)

        val result = service.cancelByBuyer(memberId, order.id, item.id!!, "변심")

        assertEquals("CANCELED", result.itemStatus)
        assertEquals(emptyList(), paymentGateway.cancelCalls)
        assertEquals(listOf(FakeBenefitReverter.RevertItem(order.id, memberId, item.id, 0, 701L, null)), benefitReverter.revertItemCalls)
    }
}
