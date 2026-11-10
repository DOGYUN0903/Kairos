package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.order.domain.OrderStatus
import com.dogyun.kairos.order.domain.SellerOrderStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrderServiceTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 11, 10, 12, 0, 0)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)

    private val orderRepository = FakeOrderRepository()
    private val productReader = FakeProductReader()
    private val sellerReader = FakeSellerReader()
    private val stockManager = FakeStockManager()
    private val paymentGateway = FakePaymentGateway()
    private val service = OrderService(
        orderRepository, productReader, sellerReader, stockManager, paymentGateway, OrderProperties(), clock, Random(7),
    )

    private val address = ShippingAddressCommand("홍길동", "010-1234-5678", "06236", "서울 강남구 테헤란로 1", "101호", null)

    @BeforeTest
    fun setUp() {
        sellerReader.put(1L)
        sellerReader.put(2L)
        productReader.put(10L, sellerId = 1L, salePrice = 9_000)
        productReader.put(20L, sellerId = 2L, salePrice = 4_000, extraPrice = 1_000)
        stockManager.stock[10L] = 5
        stockManager.stock[20L] = 5
    }

    private fun cmd(vararg items: Pair<Long, Int>, expected: Long, addr: ShippingAddressCommand = address) =
        PlaceOrderCommand(items.map { OrderItemCommand(it.first, it.second) }, expected, addr)

    private fun assertError(code: ErrorCode, block: () -> Unit) {
        assertEquals(code, assertFailsWith<DomainException> { block() }.errorCode)
    }

    @Test
    fun 생성하면_PENDING_PAYMENT_주문과_READY_결제가_만들어지고_재고가_빠진다() {
        // 9000*2 + 5000*1 = 23000 + 판매자별 배송비 3000*2
        val result = service.place(7L, cmd(10L to 2, 20L to 1, expected = 29_000))

        val order = orderRepository.findById(result.orderId)!!
        assertEquals(OrderStatus.PENDING_PAYMENT, order.status)
        assertEquals(7L, order.memberId)
        assertEquals(29_000L, order.totalAmount)
        assertEquals(2, order.sellerOrders.size)
        assertEquals(setOf(SellerOrderStatus.PENDING_PAYMENT), order.sellerOrders.map { it.status }.toSet())
        assertEquals(result.orderNo, order.orderNo)
        assertEquals(29_000L, result.totalAmount)

        val payment = paymentGateway.get(result.orderId)
        assertEquals("READY", payment.status)
        assertEquals(29_000L, payment.amount)
        assertEquals(result.orderNo, payment.orderNo)

        assertEquals(3, stockManager.stock[10L])
        assertEquals(4, stockManager.stock[20L])
    }

    @Test
    fun 같은_SKU가_두_번_오면_수량을_합쳐_한_항목으로_만든다() {
        val result = service.place(7L, cmd(10L to 1, 10L to 2, expected = 30_000))

        val items = orderRepository.findById(result.orderId)!!.allItems
        assertEquals(1, items.size)
        assertEquals(3, items.single().quantity)
        assertEquals(2, stockManager.stock[10L])
    }

    @Test
    fun 예상_총액이_다르면_ORDER_PRICE_CHANGED이고_재고는_빠지지_않는다() {
        assertError(ErrorCode.ORDER_PRICE_CHANGED) { service.place(7L, cmd(10L to 2, expected = 18_000)) }

        assertEquals(5, stockManager.stock[10L])
        assertEquals(emptyList(), orderRepository.all)
        assertEquals(emptyMap(), paymentGateway.payments)
    }

    @Test
    fun 재고_부족이면_STOCK_SOLD_OUT() {
        assertError(ErrorCode.STOCK_SOLD_OUT) { service.place(7L, cmd(10L to 6, expected = 54_000)) }
        assertEquals(emptyList(), orderRepository.all)
    }

    @Test
    fun 구매_불가_SKU는_SKU_NOT_ORDERABLE() {
        productReader.put(30L, sellerId = 1L, available = false)
        assertError(ErrorCode.SKU_NOT_ORDERABLE) { service.place(7L, cmd(30L to 1, expected = 12_000)) }
        assertError(ErrorCode.SKU_NOT_ORDERABLE) { service.place(7L, cmd(999L to 1, expected = 12_000)) }
    }

    @Test
    fun 판매자를_찾을_수_없으면_SKU_NOT_ORDERABLE() {
        sellerReader.sellers.remove(2L)
        assertError(ErrorCode.SKU_NOT_ORDERABLE) { service.place(7L, cmd(20L to 1, expected = 8_000)) }
    }

    @Test
    fun 만료_시각은_생성_15분_뒤다() {
        val result = service.place(7L, cmd(10L to 1, expected = 12_000))

        assertEquals(now.plusMinutes(15), result.expiresAt)
    }

    @Test
    fun 재고_차감은_SKU_ID_오름차순으로_호출된다() {
        service.place(7L, cmd(20L to 1, 10L to 1, expected = 20_000))

        assertEquals(listOf(10L, 20L), stockManager.calls.map { it.skuId })
    }

    @Test
    fun 수량이_범위_밖이거나_항목이_없으면_ORDER_INVALID() {
        assertError(ErrorCode.ORDER_INVALID) { service.place(7L, cmd(expected = 0)) }
        assertError(ErrorCode.ORDER_INVALID) { service.place(7L, cmd(10L to 0, expected = 3_000)) }
        assertError(ErrorCode.ORDER_INVALID) { service.place(7L, cmd(10L to -1, expected = 3_000)) }
        assertError(ErrorCode.ORDER_INVALID) { service.place(7L, cmd(10L to 1_000, expected = 3_000)) }
        // 합산하면 999 초과
        assertError(ErrorCode.ORDER_INVALID) { service.place(7L, cmd(10L to 500, 10L to 500, expected = 3_000)) }
        assertEquals(5, stockManager.stock[10L])
    }

    @Test
    fun 배송지가_비어_있으면_ORDER_INVALID() {
        assertError(ErrorCode.ORDER_INVALID) {
            service.place(7L, cmd(10L to 1, expected = 12_000, addr = address.copy(receiverName = " ")))
        }
        assertEquals(5, stockManager.stock[10L])
    }

    @Test
    fun 여러_SKU_중_두_번째가_재고_부족이면_첫_SKU_재고도_그대로다() {
        stockManager.stock[20L] = 0

        assertError(ErrorCode.STOCK_SOLD_OUT) { service.place(7L, cmd(10L to 1, 20L to 1, expected = 20_000)) }

        assertEquals(5, stockManager.stock[10L])
        assertEquals(emptyList(), orderRepository.all)
    }
}
