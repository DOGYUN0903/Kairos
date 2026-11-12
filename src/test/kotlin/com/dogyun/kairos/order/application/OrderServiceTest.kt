package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.application.InlineTransactionRunner
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
    private val benefitApplier = FakeBenefitApplier()
    private val cartItemRemover = FakeCartItemRemover()
    private val paymentService = OrderPaymentService(
        orderRepository, paymentGateway, stockManager, FakeBenefitReverter(), cartItemRemover, InlineTransactionRunner(), clock,
    )
    private val service = OrderService(
        orderRepository, productReader, sellerReader, stockManager, paymentGateway, benefitApplier, paymentService,
        OrderProperties(), InlineTransactionRunner(), clock, Random(7),
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

    // ---- C3: 쿠폰·포인트 (스펙 6장 예시: 판매자 1 의 31 = 30,000 x1, 판매자 2 의 32 = 20,000 x2) ----

    private fun spec6Fixture() {
        productReader.put(31L, sellerId = 1L, salePrice = 30_000)
        productReader.put(32L, sellerId = 2L, salePrice = 20_000)
        stockManager.stock[31L] = 5
        stockManager.stock[32L] = 5
        benefitApplier.product(501L, issuer = "PLATFORM", value = 2_000)
        benefitApplier.product(502L, issuer = "SELLER", target = "SELLER", sellerId = 2L, discountType = "RATE", value = 1_000, max = 5_000)
        benefitApplier.cart(503L, value = 1_000, max = 5_000, min = 50_000)
        benefitApplier.balance = 20_000
    }

    private fun spec6Command(
        expected: Long = 49_000,
        productCoupons: List<ProductCouponCommand> = listOf(ProductCouponCommand(31L, 501L), ProductCouponCommand(32L, 502L)),
        cartCouponId: Long? = 503L,
        points: Long = 10_000,
    ) = PlaceOrderCommand(
        listOf(OrderItemCommand(31L, 1), OrderItemCommand(32L, 2)), expected, address, productCoupons, cartCouponId, points,
    )

    @Test
    fun 쿠폰과_포인트를_쓰면_reserve_commit_순서로_호출되고_totalAmount는_paidAmount_합이다() {
        spec6Fixture()

        val result = service.place(7L, spec6Command())

        assertEquals(listOf("reserve", "commit"), benefitApplier.calls)
        assertEquals(listOf(setOf(501L, 502L, 503L)), benefitApplier.reserved)
        assertEquals(listOf(FakeBenefitApplier.Commit(7L, result.orderId, setOf(501L, 502L, 503L), 10_000)), benefitApplier.committed)
        val order = orderRepository.findById(result.orderId)!!
        assertEquals(49_000L, order.totalAmount)
        assertEquals(49_000L, result.totalAmount)
        assertEquals(order.allItems.sumOf { it.paidAmount }, order.totalAmount)
        assertEquals(mapOf(31L to 21_438L, 32L to 27_562L), order.allItems.associate { it.skuId to it.paidAmount })
        assertEquals(6_000L, order.productCouponDiscount)
        assertEquals(5_000L, order.cartCouponDiscount)
        assertEquals(10_000L, order.pointsUsed)
        assertEquals("PENDING_PAYMENT", result.orderStatus)
        assertEquals("READY", result.paymentStatus)
        assertEquals(49_000L, paymentGateway.get(result.orderId).amount)
    }

    @Test
    fun 같은_쿠폰_ID를_두_번_지정하면_ORDER_INVALID이고_쿠폰은_AVAILABLE_그대로다() {
        spec6Fixture()

        assertError(ErrorCode.ORDER_INVALID) {
            service.place(7L, spec6Command(productCoupons = listOf(ProductCouponCommand(31L, 501L), ProductCouponCommand(32L, 501L))))
        }
        assertError(ErrorCode.ORDER_INVALID) {
            service.place(7L, spec6Command(productCoupons = listOf(ProductCouponCommand(31L, 503L))))
        }
        assertError(ErrorCode.ORDER_INVALID) {
            service.place(7L, spec6Command(productCoupons = listOf(ProductCouponCommand(31L, 501L), ProductCouponCommand(31L, 502L))))
        }

        assertEquals(emptyList(), benefitApplier.reserved)
        assertEquals(emptySet(), benefitApplier.used)
    }

    @Test
    fun 항목에_없는_skuId에_쿠폰을_지정하면_ORDER_INVALID() {
        spec6Fixture()

        assertError(ErrorCode.ORDER_INVALID) {
            service.place(7L, spec6Command(productCoupons = listOf(ProductCouponCommand(10L, 501L))))
        }
        assertEquals(emptyList(), benefitApplier.reserved)
    }

    @Test
    fun 예상_총액이_다르면_ORDER_PRICE_CHANGED이고_commit과_재고_차감은_없다() {
        spec6Fixture()

        assertError(ErrorCode.ORDER_PRICE_CHANGED) { service.place(7L, spec6Command(expected = 50_000)) }

        assertEquals(emptyList(), benefitApplier.committed)
        assertEquals(emptyList(), stockManager.calls)
        assertEquals(emptyList(), orderRepository.all)
    }

    @Test
    fun 재고_부족이면_STOCK_SOLD_OUT이고_commit은_없다() {
        spec6Fixture()
        stockManager.stock[32L] = 1

        assertError(ErrorCode.STOCK_SOLD_OUT) { service.place(7L, spec6Command()) }

        assertEquals(emptyList(), benefitApplier.committed)
        assertEquals(emptyList(), orderRepository.all)
    }

    @Test
    fun `0원_주문은_place_뒤_approveWithoutPg가_불려_PAID_APPROVED다`() {
        spec6Fixture()
        benefitApplier.product(601L, value = 30_000)
        benefitApplier.product(602L, value = 40_000)

        val result = service.place(
            7L, spec6Command(expected = 0, productCoupons = listOf(ProductCouponCommand(31L, 601L), ProductCouponCommand(32L, 602L)), cartCouponId = null, points = 0),
        )

        assertEquals(0L, result.totalAmount)
        assertEquals("PAID", result.orderStatus)
        assertEquals("APPROVED", result.paymentStatus)
        val order = orderRepository.findById(result.orderId)!!
        assertEquals(OrderStatus.PAID, order.status)
        assertEquals(now, order.paidAt)
        val payment = paymentGateway.get(result.orderId)
        assertEquals("APPROVED", payment.status)
        assertEquals(null, payment.paymentKey)
        assertEquals(emptyList(), paymentGateway.confirmCalls)
        assertEquals(mapOf(131L to 1, 132L to 2), stockManager.sales)
        assertEquals(listOf(7L to setOf(31L, 32L)), cartItemRemover.removed)
    }

    @Test
    fun quote는_저장하지_않고_maxUsablePoints를_준다() {
        spec6Fixture()

        val quote = service.quote(
            7L,
            QuoteOrderCommand(
                listOf(OrderItemCommand(31L, 1), OrderItemCommand(32L, 2)),
                listOf(ProductCouponCommand(31L, 501L), ProductCouponCommand(32L, 502L)), 503L, 10_000,
            ),
        )

        assertEquals(70_000L, quote.itemsAmount)
        assertEquals(6_000L, quote.productCouponDiscount)
        assertEquals(5_000L, quote.cartCouponDiscount)
        assertEquals(10_000L, quote.pointsUsed)
        assertEquals(0L, quote.buyerShippingFee)
        assertEquals(49_000L, quote.totalAmount)
        assertEquals(17_700L, quote.maxUsablePoints)
        assertEquals(
            listOf(
                QuoteLine(31L, 131L, "티셔츠31", "브랜드", "블랙 / M", "https://img.test/sku31.jpg", 30_000, 1L, 1, 30_000, 2_000, 2_187, 4_375, 21_438),
                QuoteLine(32L, 132L, "티셔츠32", "브랜드", "블랙 / M", "https://img.test/sku32.jpg", 20_000, 2L, 2, 40_000, 4_000, 2_813, 5_625, 27_562),
            ),
            quote.items,
        )
        assertEquals(
            listOf(
                SellerQuote(1L, "판매자1", 30_000, 3_000, "PLATFORM"),
                SellerQuote(2L, "판매자2", 40_000, 3_000, "PLATFORM"),
            ),
            quote.sellerQuotes,
        )
        assertEquals(50_000L, quote.freeShippingThreshold)
        assertEquals(3_000L, quote.baseShippingFee)
        assertEquals(emptyList(), orderRepository.all)
        assertEquals(emptyList(), benefitApplier.reserved)
        assertEquals(emptyList(), benefitApplier.committed)
        assertEquals(emptyList(), stockManager.calls)
    }

    // ---- F3: 견적의 "적용 가능한 내 쿠폰" ----

    private val twoItems = listOf(OrderItemCommand(31L, 1), OrderItemCommand(32L, 2))

    private fun quoteOf(productCoupons: List<ProductCouponCommand> = emptyList(), cartCouponId: Long? = null) =
        service.quote(7L, QuoteOrderCommand(twoItems, productCoupons, cartCouponId, 0))

    private fun OrderQuote.lineCoupons(skuId: Long) = items.single { it.skuId == skuId }.availableCoupons

    @Test
    fun 견적은_항목마다_대상이_맞는_상품_쿠폰과_예상_할인을_준다() {
        spec6Fixture()
        benefitApplier.hold(501L)
        benefitApplier.hold(502L)

        val quote = quoteOf()

        assertEquals(listOf(501L to 2_000L), quote.lineCoupons(31L).map { it.couponId to it.expectedDiscount })
        assertEquals(listOf(502L to 4_000L, 501L to 2_000L), quote.lineCoupons(32L).map { it.couponId to it.expectedDiscount })
        assertEquals(true, (quote.lineCoupons(31L) + quote.lineCoupons(32L)).all { it.usable && it.unusableReason == null })
        val seller = quote.lineCoupons(32L).first()
        assertEquals(listOf("SELLER", "RATE", 1_000L, 5_000L, null), listOf(seller.issuerType, seller.discountType, seller.discountValue, seller.maxDiscount, seller.minOrderAmount))
        assertEquals(emptyList(), quote.cartCoupons)
    }

    @Test
    fun 장바구니_쿠폰은_상품_쿠폰_적용_뒤_금액으로_판정한다() {
        spec6Fixture()
        benefitApplier.hold(503L)
        benefitApplier.hold(501L)
        benefitApplier.hold(502L)

        val none = quoteOf().cartCoupons.single()
        assertEquals(true to 5_000L, none.usable to none.expectedDiscount)

        val withProduct = quoteOf(listOf(ProductCouponCommand(31L, 501L), ProductCouponCommand(32L, 502L)))
        assertEquals(64_000L, withProduct.itemsAmount - withProduct.productCouponDiscount)
        val cart = withProduct.cartCoupons.single()
        assertEquals(true to 5_000L, cart.usable to cart.expectedDiscount)
    }

    @Test
    fun 상품_쿠폰으로_합계가_최소_금액_아래로_내려가면_장바구니_쿠폰은_사용_불가다() {
        spec6Fixture()
        benefitApplier.cart(504L, min = 69_000)
        benefitApplier.cart(505L, min = 68_000)
        benefitApplier.hold(504L)
        benefitApplier.hold(505L)

        assertEquals(listOf(true, true), quoteOf().cartCoupons.map { it.usable })

        val quote = quoteOf(listOf(ProductCouponCommand(31L, 501L)))
        val byId = quote.cartCoupons.associateBy { it.couponId }
        assertEquals(false, byId.getValue(504L).usable)
        assertEquals("MIN_AMOUNT_NOT_MET", byId.getValue(504L).unusableReason)
        assertEquals(null, byId.getValue(504L).expectedDiscount)
        assertEquals(true, byId.getValue(505L).usable)
    }

    @Test
    fun 최소_금액_미달_상품_쿠폰은_사용_불가로_목록에_남는다() {
        spec6Fixture()
        benefitApplier.product(506L, min = 35_000)
        benefitApplier.hold(506L)

        val quote = quoteOf()

        val small = quote.lineCoupons(31L).single()
        assertEquals(false, small.usable)
        assertEquals("MIN_AMOUNT_NOT_MET", small.unusableReason)
        assertEquals(null, small.expectedDiscount)
        assertEquals(true, quote.lineCoupons(32L).single().usable)
    }

    @Test
    fun 상품_쿠폰이_전액을_덮으면_장바구니_쿠폰은_할인할_금액이_없다() {
        spec6Fixture()
        benefitApplier.product(507L, value = 30_000)
        benefitApplier.product(508L, value = 40_000)
        benefitApplier.cart(509L, min = null)
        benefitApplier.hold(503L)
        benefitApplier.hold(509L)

        val quote = quoteOf(listOf(ProductCouponCommand(31L, 507L), ProductCouponCommand(32L, 508L)))

        assertEquals(listOf(false, false), quote.cartCoupons.map { it.usable })
        assertEquals(listOf("NOTHING_TO_DISCOUNT", "NOTHING_TO_DISCOUNT"), quote.cartCoupons.map { it.unusableReason })
    }

    @Test
    fun 대상이_다른_쿠폰과_종류가_다른_쿠폰은_목록에_없다() {
        spec6Fixture()
        benefitApplier.product(510L, issuer = "SELLER", target = "SELLER", sellerId = 9L)
        benefitApplier.product(511L, target = "PRODUCT", productId = 999L)
        benefitApplier.hold(510L)
        benefitApplier.hold(511L)
        benefitApplier.hold(503L)
        benefitApplier.hold(501L)

        val quote = quoteOf()

        assertEquals(listOf(501L), quote.lineCoupons(31L).map { it.couponId })
        assertEquals(listOf(501L), quote.lineCoupons(32L).map { it.couponId })
        assertEquals(listOf(503L), quote.cartCoupons.map { it.couponId })
    }

    @Test
    fun 정렬은_사용_가능_먼저_예상_할인_큰_순_만료_빠른_순이다() {
        spec6Fixture()
        benefitApplier.product(512L, value = 1_000)
        benefitApplier.product(513L, value = 1_000)
        benefitApplier.product(514L, value = 9_000, min = 99_000)
        benefitApplier.hold(512L, expiresAt = LocalDateTime.of(2026, 12, 20, 0, 0))
        benefitApplier.hold(513L, expiresAt = LocalDateTime.of(2026, 12, 10, 0, 0))
        benefitApplier.hold(514L)
        benefitApplier.hold(501L)

        assertEquals(listOf(501L, 513L, 512L, 514L), quoteOf().lineCoupons(31L).map { it.couponId })
    }

    @Test
    fun usable인_쿠폰은_고르면_성공하고_아닌_쿠폰은_거절된다() {
        spec6Fixture()
        benefitApplier.product(506L, min = 35_000)
        benefitApplier.product(507L, value = 30_000)
        benefitApplier.product(508L, value = 40_000)
        benefitApplier.cart(504L, min = 69_000)
        benefitApplier.cart(509L, min = null)
        listOf(501L, 502L, 503L, 504L, 506L, 507L, 508L, 509L).forEach(benefitApplier::hold)

        // (가) 상품 쿠폰: 그 쿠폰 하나만 담아 견적한다.
        val base = quoteOf()
        for (line in base.items) for (coupon in line.availableCoupons) {
            val cmd = listOf(ProductCouponCommand(line.skuId, coupon.couponId))
            if (coupon.usable) {
                val result = quoteOf(cmd).items.single { it.skuId == line.skuId }
                assertEquals(coupon.expectedDiscount, result.productCouponDiscount)
            } else {
                assertError(ErrorCode.COUPON_MIN_AMOUNT_NOT_MET) { quoteOf(cmd) }
            }
        }
        // (나) 장바구니 쿠폰: 같은 상품 쿠폰 선택에 cartCouponId 만 더한다.
        val selections = listOf(
            emptyList(),
            listOf(ProductCouponCommand(31L, 501L)),
            listOf(ProductCouponCommand(31L, 507L), ProductCouponCommand(32L, 508L)),
        )
        var checked = 0
        for (p in selections) {
            for (coupon in quoteOf(p).cartCoupons) {
                checked++
                if (coupon.usable) {
                    assertEquals(coupon.expectedDiscount, quoteOf(p, coupon.couponId).cartCouponDiscount)
                } else when (coupon.unusableReason) {
                    "MIN_AMOUNT_NOT_MET" -> assertError(ErrorCode.COUPON_MIN_AMOUNT_NOT_MET) { quoteOf(p, coupon.couponId) }
                    "NOTHING_TO_DISCOUNT" -> assertError(ErrorCode.COUPON_NOT_APPLICABLE) { quoteOf(p, coupon.couponId) }
                    else -> error("알 수 없는 이유: ${coupon.unusableReason}")
                }
            }
        }
        assertEquals(true, checked > 0)
    }
}
