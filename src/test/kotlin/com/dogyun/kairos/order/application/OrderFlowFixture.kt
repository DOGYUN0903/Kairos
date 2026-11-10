package com.dogyun.kairos.order.application

import com.dogyun.kairos.order.domain.Order
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

/** 정리 작업 단위 테스트 공통 준비물. 판매자 1·2 각각 30,000원 SKU 하나(재고 10), 무료배송 주문 60,000원. */
class OrderFlowFixture {
    val zone: ZoneId = ZoneId.of("Asia/Seoul")
    val now: LocalDateTime = LocalDateTime.of(2026, 11, 10, 12, 0, 0)
    val clock: Clock = clockAt(now)

    val orderRepository = FakeOrderRepository()
    val productReader = FakeProductReader()
    val sellerReader = FakeSellerReader()
    val stockManager = FakeStockManager()
    val benefitReverter = FakeBenefitReverter(stockManager.events)
    val paymentGateway = FakePaymentGateway()
    val tx = InlineTransactionRunner()
    val properties = OrderProperties()

    val benefitApplier = FakeBenefitApplier()
    val paymentService = OrderPaymentService(orderRepository, paymentGateway, stockManager, benefitReverter, FakeCartItemRemover(), tx, clock)
    val orderService = OrderService(
        orderRepository, productReader, sellerReader, stockManager, paymentGateway, benefitApplier, paymentService, properties, tx, clock, Random(7),
    )
    val cancelService = OrderCancelService(orderRepository, paymentGateway, stockManager, benefitReverter, properties, tx, clock)
    val shippingService = OrderShippingService(orderRepository, tx, clock)

    val memberId = 7L
    private val address = ShippingAddressCommand("홍길동", "010-1234-5678", "06236", "서울 강남구 테헤란로 1", "101호", null)

    init {
        sellerReader.put(1L)
        sellerReader.put(2L)
        productReader.put(10L, sellerId = 1L, salePrice = 30_000)
        productReader.put(20L, sellerId = 2L, salePrice = 30_000)
        stockManager.stock[10L] = 10
        stockManager.stock[20L] = 10
    }

    fun clockAt(time: LocalDateTime): Clock = Clock.fixed(time.atZone(zone).toInstant(), zone)

    /** PENDING_PAYMENT 주문(결제 READY). 재고는 9, 9 가 된다. */
    fun place(): Order {
        val placed = orderService.place(memberId, PlaceOrderCommand(listOf(OrderItemCommand(10L, 1), OrderItemCommand(20L, 1)), 60_000, address))
        return reload(placed.orderId)
    }

    /** 결제를 IN_PROGRESS 로 선점한다. [unknown] 이면 UNKNOWN 까지 간다. */
    fun startPayment(order: Order, paymentKey: String, unknown: Boolean = false) {
        paymentGateway.start(order.id!!, paymentKey)
        if (unknown) paymentGateway.markUnknown(order.id!!)
    }

    fun pgDone(paymentKey: String, status: PgPaymentStatus = PgPaymentStatus.DONE, total: Long = 60_000, balance: Long = total, cancels: List<PgCancelInfo> = emptyList()) {
        paymentGateway.pgPayments[paymentKey] = PgOutcome.Done(PgPaymentInfo(status, total, balance, cancels))
    }

    fun pgCancelDone(amount: Long = 60_000, balance: Long = 0) {
        paymentGateway.nextCancel = PgOutcome.Done(
            PgPaymentInfo(if (balance == 0L) PgPaymentStatus.CANCELED else PgPaymentStatus.PARTIAL_CANCELED, amount + balance, balance, listOf(PgCancelInfo(amount, "사유"))),
        )
    }

    /** 주문을 만들고 승인해 PAID 로 만든다. */
    fun paid(): Order {
        val placed = place()
        paymentGateway.nextConfirm = PgOutcome.Done(PgPaymentInfo(PgPaymentStatus.DONE, 60_000, 60_000, emptyList()))
        paymentService.confirm(memberId, ConfirmPaymentCommand(placed.orderNo, "pay-${placed.id}", 60_000))
        return reload(placed.id!!)
    }

    fun reload(order: Order): Order = reload(order.id!!)
    fun reload(orderId: Long): Order = orderRepository.findById(orderId)!!
}
