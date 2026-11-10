package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.CommerceTestSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.ResultActionsDsl
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrderApiTest : CommerceTestSupport() {

    private class Shop(val sellerToken: String, val productId: Long, val skuIds: List<Long>)

    /** 판매자 한 명과 판매가 [salePrice], 모든 SKU 재고 [stock] 인 상품 하나. SKU ID 는 오름차순. */
    private fun shop(salePrice: Long = 8_000, stock: Int = 5): Shop {
        val token = newUserToken()
        approvedSeller(token)
        val productId = onSaleProduct(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)), originalPrice = maxOf(10_000, salePrice), salePrice = salePrice, stock = stock)
        return Shop(token, productId, skuIdsOf(token, productId).sorted())
    }

    private fun stockOf(shop: Shop, skuId: Long): Int =
        sellerProductDetail(shop.sellerToken, shop.productId).get("skus").first { it.get("skuId").asLong() == skuId }.get("stock").asInt()

    private fun orderCount(): Int = jdbcTemplate.queryForObject("select count(*) from orders", Int::class.java)!!

    private fun ResultActionsDsl.expectCode(status: Int, code: String) {
        andExpect {
            status { isEqualTo(status) }
            jsonPath("$.code") { value(code) }
        }
    }

    @Test
    fun 두_판매자_상품을_주문하면_201과_주문번호_총액_만료시각을_받고_재고가_줄어든다() {
        val a = shop()
        val b = shop()
        val user = newUserToken()

        // 8000*2 + 8000*1 = 24000 (50000 미만이라 판매자별 배송비 3000*2)
        val data = json(placeOrder(user, listOf(a.skuIds[0] to 2, b.skuIds[0] to 1), 30_000).andExpect { status { isCreated() } }).get("data")

        assertTrue(data.get("orderId").asLong() > 0)
        val orderNo = data.get("orderNo").asString()
        assertEquals(21, orderNo.length)
        assertTrue(orderNo.startsWith("K"))
        assertEquals(30_000L, data.get("totalAmount").asLong())
        val expiresAt = LocalDateTime.parse(data.get("expiresAt").asString())
        assertTrue(expiresAt.isAfter(LocalDateTime.now().plusMinutes(14)) && expiresAt.isBefore(LocalDateTime.now().plusMinutes(16)))
        assertEquals(3, stockOf(a, a.skuIds[0]))
        assertEquals(4, stockOf(b, b.skuIds[0]))
        assertEquals("PENDING_PAYMENT", jdbcTemplate.queryForObject("select order_status from orders", String::class.java))
        assertEquals("READY", jdbcTemplate.queryForObject("select payment_status from payment", String::class.java))
    }

    @Test
    fun 예상_총액이_다르면_409_ORDER_PRICE_CHANGED() {
        val a = shop()
        val user = newUserToken()

        placeOrder(user, listOf(a.skuIds[0] to 1), 1).expectCode(409, "ORDER_PRICE_CHANGED")

        assertEquals(5, stockOf(a, a.skuIds[0]))
        assertEquals(0, orderCount())
    }

    @Test
    fun 구매할_수_없는_SKU는_409_SKU_NOT_ORDERABLE() {
        val a = shop()
        val user = newUserToken()
        patch(
            "/api/v1/seller/products/${a.productId}/skus/${a.skuIds[0]}", a.sellerToken,
            mapOf("extraPrice" to 0, "stock" to 5, "onSale" to false),
        ).andExpect { status { isOk() } }

        placeOrder(user, listOf(a.skuIds[0] to 1), 11_000).expectCode(409, "SKU_NOT_ORDERABLE")
    }

    @Test
    fun 로그인하지_않으면_401() {
        placeOrder("invalid", listOf(1L to 1), 11_000).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun Idempotency_Key가_없으면_400() {
        val a = shop()
        val user = newUserToken()
        val body = mapOf(
            "items" to listOf(mapOf("skuId" to a.skuIds[0], "quantity" to 1)),
            "expectedTotalAmount" to 11_000,
            "shippingAddress" to address(),
        )

        post("/api/v1/orders", user, body).expectCode(400, "IDEMPOTENCY_KEY_REQUIRED")

        assertEquals(0, orderCount())
    }

    @Test
    fun 같은_키_재요청은_같은_orderId를_돌려주고_주문은_하나다() {
        val a = shop()
        val user = newUserToken()
        val key = UUID.randomUUID().toString()

        val first = json(placeOrder(user, listOf(a.skuIds[0] to 1), 11_000, key).andExpect { status { isCreated() } })
        val second = json(placeOrder(user, listOf(a.skuIds[0] to 1), 11_000, key).andExpect { status { isCreated() } })

        assertEquals(first.get("data").get("orderId").asLong(), second.get("data").get("orderId").asLong())
        assertEquals(first.get("data").get("orderNo").asString(), second.get("data").get("orderNo").asString())
        assertEquals(1, orderCount())
        assertEquals(4, stockOf(a, a.skuIds[0]))
    }

    @Test
    fun 같은_키_다른_본문은_422() {
        val a = shop()
        val user = newUserToken()
        val key = UUID.randomUUID().toString()
        placeOrder(user, listOf(a.skuIds[0] to 1), 11_000, key).andExpect { status { isCreated() } }

        placeOrder(user, listOf(a.skuIds[0] to 2), 19_000, key).expectCode(422, "IDEMPOTENCY_KEY_REUSED")

        assertEquals(1, orderCount())
    }

    @Test
    fun 여러_SKU_중_뒤_SKU가_재고_부족이면_앞_SKU_재고도_그대로다() {
        val a = shop()
        val user = newUserToken()
        patch(
            "/api/v1/seller/products/${a.productId}/skus/${a.skuIds[1]}", a.sellerToken,
            mapOf("extraPrice" to 0, "stock" to 2, "onSale" to true),
        ).andExpect { status { isOk() } }

        // 8000 * 4 = 32000 + 3000. 첫 SKU 는 차감되지만 둘째(재고 2, 수량 3)에서 실패해 전부 롤백된다.
        placeOrder(user, listOf(a.skuIds[0] to 1, a.skuIds[1] to 3), 35_000).expectCode(409, "STOCK_SOLD_OUT")

        assertEquals(5, stockOf(a, a.skuIds[0]))
        assertEquals(2, stockOf(a, a.skuIds[1]))
        assertEquals(0, orderCount())
        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from payment", Int::class.java))
    }

    @Test
    fun 같은_키_동시_요청_10개는_주문_하나만_만든다() {
        val a = shop()
        val user = newUserToken()
        val key = UUID.randomUUID().toString()

        val results = runConcurrently(10) {
            val result = placeOrder(user, listOf(a.skuIds[0] to 1), 11_000, key).andReturn().response
            result.status to result.contentAsString
        }

        val created = results.filter { it.first == 201 }
        assertTrue(created.isNotEmpty())
        assertEquals(1, created.map { json(it.second).get("data").get("orderId").asLong() }.toSet().size)
        results.filter { it.first != 201 }.forEach {
            assertEquals(409, it.first)
            assertEquals("IDEMPOTENCY_IN_PROGRESS", json(it.second).get("code").asString())
        }
        assertEquals(1, orderCount())
        assertEquals(4, stockOf(a, a.skuIds[0]))
    }

    @Test
    fun 재고_3인_SKU를_10명이_동시에_1개씩_주문하면_3건만_201이고_재고는_0이다() {
        val a = shop(stock = 3)
        val users = List(10) { newUserToken() }

        val statuses = runConcurrently(10) { index ->
            val result = placeOrder(users[index], listOf(a.skuIds[0] to 1), 11_000).andReturn().response
            result.status to (if (result.status == 201) "" else json(result).get("code").asString())
        }

        assertEquals(3, statuses.count { it.first == 201 })
        statuses.filter { it.first != 201 }.forEach { assertEquals(409 to "STOCK_SOLD_OUT", it) }
        assertEquals(0, stockOf(a, a.skuIds[0]))
        assertEquals(3, orderCount())
    }

    // ---- C3: 쿠폰·포인트 ----

    /** 스펙 6장 준비: 판매자 B 30,000 x1, 판매자 C 20,000 x2, 플랫폼 정액 2,000·C 의 판매자 정률 10%(최대 5,000)·장바구니 10%(최대 5,000, 최소 50,000), 포인트 20,000. */
    private class Spec6(val user: String, val b: Shop, val c: Shop, val platformCoupon: Long, val sellerCoupon: Long, val cartCoupon: Long)

    private fun spec6(): Spec6 {
        val b = shop(salePrice = 30_000)
        val c = shop(salePrice = 20_000)
        val user = newUserToken()
        val platform = adminCouponPolicy(couponPolicyBody())
        val seller = sellerCouponPolicy(c.sellerToken, couponPolicyBody(targetType = "SELLER", discountType = "RATE", discountValue = 1_000, maxDiscount = 5_000))
        val cart = adminCouponPolicy(
            couponPolicyBody(couponType = "CART", targetType = "NONE", discountType = "RATE", discountValue = 1_000, maxDiscount = 5_000, minOrderAmount = 50_000),
        )
        grantPoints(memberIdOf(user), 20_000)
        return Spec6(user, b, c, claimCoupon(user, platform), claimCoupon(user, seller), claimCoupon(user, cart))
    }

    private fun couponStatus(couponId: Long): String =
        jdbcTemplate.queryForObject("select coupon_status from coupon where id = ?", String::class.java, couponId)!!

    @Test
    fun 스펙_6장_예시대로_주문하면_PG_금액이_49000이고_항목별_paidAmount가_21438_27562다() {
        val f = spec6()
        val bSku = f.b.skuIds[0]
        val cSku = f.c.skuIds[0]

        val placed = json(
            placeOrder(
                f.user, listOf(bSku to 1, cSku to 2), 49_000,
                productCoupons = listOf(bSku to f.platformCoupon, cSku to f.sellerCoupon), cartCouponId = f.cartCoupon, pointsToUse = 10_000,
            ).andExpect { status { isCreated() } },
        ).get("data")
        assertEquals(49_000L, placed.get("totalAmount").asLong())
        assertEquals("PENDING_PAYMENT", placed.get("orderStatus").asString())
        assertEquals("READY", placed.get("paymentStatus").asString())
        val orderId = placed.get("orderId").asLong()

        confirmPayment(f.user, placed.get("orderNo").asString(), "pay-${UUID.randomUUID()}", 49_000).andExpect { status { isOk() } }
        val detail = json(get("/api/v1/orders/$orderId", f.user).andExpect { status { isOk() } }).get("data")
        assertEquals(70_000L, detail.get("itemsAmount").asLong())
        assertEquals(6_000L, detail.get("productCouponDiscount").asLong())
        assertEquals(f.cartCoupon, detail.get("cartCouponId").asLong())
        assertEquals(5_000L, detail.get("cartCouponDiscount").asLong())
        assertEquals(10_000L, detail.get("pointsUsed").asLong())
        assertEquals(49_000L, detail.get("totalAmount").asLong())
        assertEquals(49_000L, detail.get("payment").get("amount").asLong())
        assertEquals("APPROVED", detail.get("payment").get("status").asString())
        val items = detail.get("sellerOrders").flatMap { it.get("items") }.associateBy { it.get("skuId").asLong() }
        assertEquals(21_438L, items.getValue(bSku).get("paidAmount").asLong())
        assertEquals(27_562L, items.getValue(cSku).get("paidAmount").asLong())
        assertEquals(2_187L, items.getValue(bSku).get("cartCouponAllocated").asLong())
        assertEquals(5_625L, items.getValue(cSku).get("pointsAllocated").asLong())
        assertEquals("SELLER", items.getValue(cSku).get("productCouponIssuer").asString())
        assertEquals(f.platformCoupon, items.getValue(bSku).get("productCouponId").asLong())

        assertEquals(listOf("USED", "USED", "USED"), listOf(f.platformCoupon, f.sellerCoupon, f.cartCoupon).map(::couponStatus))
        assertEquals(
            3,
            jdbcTemplate.queryForObject("select count(*) from coupon where used_order_id = ?", Int::class.java, orderId),
        )
        val memberId = memberIdOf(f.user)
        assertEquals(10_000L, pointBalance(memberId))
        assertEquals(10_000L, lotRemainingSum(memberId))
    }

    @Test
    fun 같은_쿠폰으로_두_주문을_동시에_만들면_하나만_201이고_실패한_주문은_재고와_포인트까지_롤백된다() {
        val a = shop(salePrice = 30_000)
        val user = newUserToken()
        val couponId = claimCoupon(user, adminCouponPolicy(couponPolicyBody()))
        val memberId = memberIdOf(user)
        grantPoints(memberId, 5_000)

        // 30,000 - 쿠폰 2,000 - 포인트 1,000 + 배송비 3,000
        val results = runConcurrently(2) {
            val response = placeOrder(
                user, listOf(a.skuIds[0] to 1), 30_000, productCoupons = listOf(a.skuIds[0] to couponId), pointsToUse = 1_000,
            ).andReturn().response
            response.status to (if (response.status == 201) "" else json(response).get("code").asString())
        }

        assertEquals(1, results.count { it.first == 201 })
        assertEquals(listOf(409 to "COUPON_NOT_AVAILABLE"), results.filter { it.first != 201 })
        assertEquals(1, orderCount())
        assertEquals(4, stockOf(a, a.skuIds[0]))
        assertEquals(4_000L, pointBalance(memberId))
        assertEquals(4_000L, lotRemainingSum(memberId))
        assertEquals("USED", couponStatus(couponId))
    }

    @Test
    fun 잔액_1000P로_두_주문이_각_1000P를_동시에_쓰면_하나만_성공하고_잔액은_0이다() {
        val a = shop(salePrice = 30_000)
        val user = newUserToken()
        val memberId = memberIdOf(user)
        grantPoints(memberId, 1_000)

        val results = runConcurrently(2) {
            val response = placeOrder(user, listOf(a.skuIds[0] to 1), 32_000, pointsToUse = 1_000).andReturn().response
            response.status to (if (response.status == 201) "" else json(response).get("code").asString())
        }

        assertEquals(1, results.count { it.first == 201 })
        // 뒤에 락을 잡은 주문은 잔액 0 을 보므로 최소 사용 잔액(1,000P) 미달이다(스펙 5-3).
        assertEquals(listOf(400 to "POINT_EXCEEDS_LIMIT"), results.filter { it.first != 201 })
        assertEquals(1, orderCount())
        assertEquals(4, stockOf(a, a.skuIds[0]))
        assertEquals(0L, pointBalance(memberId))
        assertEquals(0L, lotRemainingSum(memberId))
    }

    @Autowired
    private lateinit var mockPgClient: com.dogyun.kairos.payment.infrastructure.MockPgClient

    /** Mock PG 가 이 주문번호로 승인한 기록이 있는지. */
    private fun pgApproved(orderNo: String): Boolean {
        val field = mockPgClient.javaClass.getDeclaredField("payments").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val payments = field.get(mockPgClient) as Map<String, com.dogyun.kairos.payment.application.PgPayment>
        return payments.values.any { it.pgOrderId == orderNo }
    }

    /** 50,000원 상품을 장바구니에 담고 50,000원 정액 쿠폰으로 0원 주문(무료배송)을 만든다. */
    private fun zeroWonOrder(): Triple<String, Shop, JsonNode> {
        val a = shop(salePrice = 50_000)
        val user = newUserToken()
        addToCart(user, a.skuIds[0], 1)
        val couponId = claimCoupon(user, adminCouponPolicy(couponPolicyBody(discountValue = 50_000)))
        val placed = json(
            placeOrder(user, listOf(a.skuIds[0] to 1), 0, productCoupons = listOf(a.skuIds[0] to couponId)).andExpect { status { isCreated() } },
        ).get("data")
        return Triple(user, a, placed)
    }

    @Test
    fun 쿠폰과_포인트로_전액_결제한_주문은_PG_없이_PAID이고_판매량과_장바구니가_승인과_같다() {
        val (user, a, placed) = zeroWonOrder()

        assertEquals(0L, placed.get("totalAmount").asLong())
        assertEquals("PAID", placed.get("orderStatus").asString())
        assertEquals("APPROVED", placed.get("paymentStatus").asString())
        val detail = json(get("/api/v1/orders/${placed.get("orderId").asLong()}", user).andExpect { status { isOk() } }).get("data")
        assertEquals("PAID", detail.get("status").asString())
        assertTrue(!detail.get("paidAt").isNull)
        assertEquals("APPROVED", detail.get("payment").get("status").asString())
        assertTrue(detail.get("payment").get("paymentKey").isNull)
        assertTrue(!pgApproved(placed.get("orderNo").asString()))
        assertEquals(1L, sellerProductDetail(a.sellerToken, a.productId).get("salesCount").asLong())
        assertEquals(0, json(get("/api/v1/cart", user).andExpect { status { isOk() } }).get("data").get("items").size())
    }

    @Test
    fun `0원_주문을_payments_confirm으로_다시_승인하면_PG_없이_200이다`() {
        val (user, _, placed) = zeroWonOrder()
        val orderNo = placed.get("orderNo").asString()

        val confirmed = json(confirmPayment(user, orderNo, "pay-${UUID.randomUUID()}", 0).andExpect { status { isOk() } }).get("data")

        assertEquals("PAID", confirmed.get("orderStatus").asString())
        assertEquals("APPROVED", confirmed.get("paymentStatus").asString())
        assertTrue(!pgApproved(orderNo))
        assertEquals(null, jdbcTemplate.queryForObject("select payment_key from payment where order_id = ?", String::class.java, placed.get("orderId").asLong()))
    }

    @Test
    fun 종료된_정책의_쿠폰은_만료_전이면_주문에_쓸_수_있다() {
        val a = shop(salePrice = 30_000)
        val user = newUserToken()
        val policyId = adminCouponPolicy(couponPolicyBody())
        val couponId = claimCoupon(user, policyId)
        patch("/api/v1/admin/coupons/policies/$policyId/end", adminToken()).andExpect { status { isOk() } }

        placeOrder(user, listOf(a.skuIds[0] to 1), 31_000, productCoupons = listOf(a.skuIds[0] to couponId))
            .andExpect { status { isCreated() } }
        assertEquals("USED", couponStatus(couponId))
    }

    private fun json(result: org.springframework.mock.web.MockHttpServletResponse): JsonNode =
        objectMapper.readTree(result.contentAsString)

    private fun json(body: String): JsonNode = objectMapper.readTree(body)

    /** [count] 개 작업을 동시에 출발시켜 결과를 모은다. */
    private fun <T> runConcurrently(count: Int, task: (Int) -> T): List<T> {
        val pool = Executors.newFixedThreadPool(count)
        try {
            val ready = CountDownLatch(count)
            val go = CountDownLatch(1)
            val futures = (0 until count).map { index ->
                pool.submit(Callable {
                    ready.countDown()
                    go.await()
                    task(index)
                })
            }
            ready.await()
            go.countDown()
            return futures.map { it.get() }
        } finally {
            pool.shutdown()
        }
    }
}
