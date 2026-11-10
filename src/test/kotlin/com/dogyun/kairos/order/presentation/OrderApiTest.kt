package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.CommerceTestSupport
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
        val productId = onSaleProduct(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)), salePrice = salePrice, stock = stock)
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
