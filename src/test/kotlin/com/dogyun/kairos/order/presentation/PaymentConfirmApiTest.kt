package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.CommerceTestSupport
import com.dogyun.kairos.payment.application.PgClient
import com.dogyun.kairos.payment.application.PgResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.ResultActionsDsl
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaymentConfirmApiTest : CommerceTestSupport() {

    @Autowired
    private lateinit var pgClient: PgClient

    private class Shop(val sellerToken: String, val productId: Long, val skuIds: List<Long>)

    private fun shop(salePrice: Long = 8_000, stock: Int = 5): Shop {
        val token = newUserToken()
        approvedSeller(token)
        val productId = onSaleProduct(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)), salePrice = salePrice, stock = stock)
        return Shop(token, productId, skuIdsOf(token, productId).sorted())
    }

    private fun stockOf(shop: Shop, skuId: Long): Int =
        sellerProductDetail(shop.sellerToken, shop.productId).get("skus").first { it.get("skuId").asLong() == skuId }.get("stock").asInt()

    private fun salesCountOf(shop: Shop): Long = sellerProductDetail(shop.sellerToken, shop.productId).get("salesCount").asLong()

    private fun orderStatusOf(orderNo: String): String =
        jdbcTemplate.queryForObject("select order_status from orders where order_no = ?", String::class.java, orderNo)!!

    private fun paymentStatusOf(orderNo: String): String =
        jdbcTemplate.queryForObject("select payment_status from payment where order_no = ?", String::class.java, orderNo)!!

    /** 8000 * [quantity] + 배송비 3000 (5만원 미만). */
    private fun place(user: String, shop: Shop, quantity: Int = 1): Pair<String, Long> {
        val total = 8_000L * quantity + 3_000
        val orderNo = json(placeOrder(user, listOf(shop.skuIds[0] to quantity), total).andExpect { status { isCreated() } })
            .get("data").get("orderNo").asString()
        return orderNo to total
    }

    /** Mock PG 는 테스트 사이에 상태를 공유하므로 키는 매번 새로 만든다. */
    private fun key(prefix: String = "pay") = "$prefix-${UUID.randomUUID()}"

    private fun ResultActionsDsl.expectCode(status: Int, code: String) {
        andExpect {
            status { isEqualTo(status) }
            jsonPath("$.code") { value(code) }
        }
    }

    @Test
    fun 생성_승인하면_주문이_PAID이고_장바구니에서_빠지고_판매량이_오른다() {
        val shop = shop()
        val user = newUserToken()
        addToCart(user, shop.skuIds[0], 2)
        val (orderNo, total) = place(user, shop, 2)

        val data = json(confirmPayment(user, orderNo, key(), total).andExpect { status { isOk() } }).get("data")

        assertEquals(orderNo, data.get("orderNo").asString())
        assertEquals("PAID", data.get("orderStatus").asString())
        assertEquals("APPROVED", data.get("paymentStatus").asString())
        assertEquals("PAID", orderStatusOf(orderNo))
        assertEquals("APPROVED", paymentStatusOf(orderNo))
        assertEquals(3, stockOf(shop, shop.skuIds[0]))
        assertEquals(2L, salesCountOf(shop))
        assertEquals(0, json(get("/api/v1/cart", user).andExpect { status { isOk() } }).get("data").get("items").size())
    }

    @Test
    fun 새_멱등_키로_같은_paymentKey를_다시_승인하면_PG_없이_200이고_판매량은_한_번만_오른다() {
        val shop = shop()
        val user = newUserToken()
        val (orderNo, total) = place(user, shop)
        val payKey = key()

        confirmPayment(user, orderNo, payKey, total).andExpect { status { isOk() } }
        confirmPayment(user, orderNo, payKey, total).andExpect { status { isOk() } }

        assertEquals(1L, salesCountOf(shop))
    }

    @Test
    fun 같은_paymentKey로_다른_주문을_승인하면_409_PAYMENT_KEY_DUPLICATED이고_두_번째_주문은_PENDING_PAYMENT다() {
        val shop = shop()
        val user = newUserToken()
        val (first, firstTotal) = place(user, shop)
        val (second, secondTotal) = place(user, shop)
        val dupKey = key()
        confirmPayment(user, first, dupKey, firstTotal).andExpect { status { isOk() } }

        confirmPayment(user, second, dupKey, secondTotal).expectCode(409, "PAYMENT_KEY_DUPLICATED")

        assertEquals("PENDING_PAYMENT", orderStatusOf(second))
        assertEquals("READY", paymentStatusOf(second))
        assertEquals("PAID", orderStatusOf(first))
    }

    @Test
    fun fail_키는_402이고_재고가_돌아온다() {
        val shop = shop()
        val user = newUserToken()
        val (orderNo, total) = place(user, shop, 2)
        assertEquals(3, stockOf(shop, shop.skuIds[0]))

        confirmPayment(user, orderNo, key("fail"), total).expectCode(402, "PAYMENT_REJECTED")

        assertEquals("CANCELED", orderStatusOf(orderNo))
        assertEquals("FAILED", paymentStatusOf(orderNo))
        assertEquals(5, stockOf(shop, shop.skuIds[0]))
        assertEquals(0L, salesCountOf(shop))
    }

    @Test
    fun unknown_키는_200_UNKNOWN이고_주문은_PENDING_PAYMENT다() {
        val shop = shop()
        val user = newUserToken()
        val (orderNo, total) = place(user, shop)

        val data = json(confirmPayment(user, orderNo, key("unknown"), total).andExpect { status { isOk() } }).get("data")

        assertEquals("UNKNOWN", data.get("paymentStatus").asString())
        assertEquals("PENDING_PAYMENT", data.get("orderStatus").asString())
        assertEquals("PENDING_PAYMENT", orderStatusOf(orderNo))
        assertEquals("UNKNOWN", paymentStatusOf(orderNo))
        assertEquals(4, stockOf(shop, shop.skuIds[0]))
    }

    @Test
    fun 금액이_다르면_400() {
        val shop = shop()
        val user = newUserToken()
        val (orderNo, total) = place(user, shop)
        val payKey = key()

        confirmPayment(user, orderNo, payKey, total - 1).expectCode(400, "PAYMENT_AMOUNT_MISMATCH")

        assertEquals("READY", paymentStatusOf(orderNo))
        assertTrue(pgClient.find(payKey) is PgResult.Failed)
    }

    @Test
    fun 남의_주문은_404다() {
        val shop = shop()
        val owner = newUserToken()
        val (orderNo, total) = place(owner, shop)

        confirmPayment(newUserToken(), orderNo, key(), total).expectCode(404, "ORDER_NOT_FOUND")
    }

    @Test
    fun Idempotency_Key가_없으면_400() {
        val shop = shop()
        val user = newUserToken()
        val (orderNo, total) = place(user, shop)

        post("/api/v1/payments/confirm", user, mapOf("orderNo" to orderNo, "paymentKey" to key(), "amount" to total))
            .expectCode(400, "IDEMPOTENCY_KEY_REQUIRED")
    }

    @Test
    fun 같은_주문을_서로_다른_paymentKey로_동시에_승인하면_PG_승인은_한_번이고_나머지는_409다() {
        val shop = shop()
        val user = newUserToken()
        val (orderNo, total) = place(user, shop)
        val keys = listOf("conc-a-${UUID.randomUUID()}", "conc-b-${UUID.randomUUID()}")

        val results = runConcurrently(2) { index ->
            val response = confirmPayment(user, orderNo, keys[index], total).andReturn().response
            response.status to response.contentAsString
        }

        assertEquals(1, results.count { it.first == 200 })
        results.filter { it.first != 200 }.forEach {
            assertEquals(409, it.first)
            assertTrue(json(it.second).get("code").asString() in setOf("PAYMENT_IN_PROGRESS", "ORDER_INVALID_STATE_TRANSITION"))
        }
        assertEquals(1, keys.count { pgClient.find(it) is PgResult.Done })
        assertEquals("PAID", orderStatusOf(orderNo))
        assertEquals(1L, salesCountOf(shop))
    }

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

    private fun json(body: String) = objectMapper.readTree(body)
}
