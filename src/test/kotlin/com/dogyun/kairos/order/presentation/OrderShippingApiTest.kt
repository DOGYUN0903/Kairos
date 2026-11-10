package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.CommerceTestSupport
import org.springframework.test.web.servlet.ResultActionsDsl
import tools.jackson.databind.JsonNode
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrderShippingApiTest : CommerceTestSupport() {

    private class Shop(val sellerToken: String, val skuId: Long)

    private fun shop(salePrice: Long = 20_000): Shop {
        val token = newUserToken()
        approvedSeller(token)
        val productId = onSaleProduct(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)), originalPrice = salePrice, salePrice = salePrice, stock = 5)
        return Shop(token, skuIdsOf(token, productId).sorted()[0])
    }

    private class Paid(val orderId: Long, val sellerOrderId: Long, val itemId: Long)

    /** 상점 한 곳, 한 개 주문(3,000원 배송비 포함 아님: 50,000 이상이면 무료). 금액은 [price]+배송비. */
    private fun paid(user: String, shop: Shop, price: Long): Paid {
        val total = if (price >= 50_000) price else price + 3_000
        val order = paidOrder(user, listOf(shop.skuId to 1), total)
        val so = order.get("sellerOrders").get(0)
        return Paid(order.get("orderId").asLong(), so.get("sellerOrderId").asLong(), so.get("items").get(0).get("orderItemId").asLong())
    }

    private fun sellerOrderStatus(id: Long) =
        jdbcTemplate.queryForObject("select seller_order_status from seller_order where id = ?", String::class.java, id)!!

    private fun itemStatus(id: Long) =
        jdbcTemplate.queryForObject("select order_item_status from order_item where id = ?", String::class.java, id)!!

    private fun orderStatus(id: Long) =
        jdbcTemplate.queryForObject("select order_status from orders where id = ?", String::class.java, id)!!

    private fun ship(token: String, soId: Long, tracking: String = "T-1"): ResultActionsDsl =
        patch("/api/v1/seller/orders/$soId/ship", token, mapOf("carrierCode" to "04", "trackingNumber" to tracking))

    private fun deliver(token: String, soId: Long) = patch("/api/v1/seller/orders/$soId/deliver", token)

    private fun confirm(user: String, p: Paid) = patch("/api/v1/orders/${p.orderId}/seller-orders/${p.sellerOrderId}/confirm", user)

    private fun cancel(user: String, p: Paid): ResultActionsDsl =
        postIdem("/api/v1/orders/${p.orderId}/items/${p.itemId}/cancel", user, mapOf("reason" to "변심"), UUID.randomUUID().toString())

    private fun ResultActionsDsl.expectCode(status: Int, code: String) {
        andExpect {
            status { isEqualTo(status) }
            jsonPath("$.code") { value(code) }
        }
    }

    @Test
    fun 배송_시작_완료_구매_확정까지_가면_판매자_주문_CONFIRMED_주문_COMPLETED다() {
        val shop = shop()
        val user = newUserToken()
        val p = paid(user, shop, 20_000)

        ship(shop.sellerToken, p.sellerOrderId).andExpect { status { isOk() }; jsonPath("$.data.sellerOrderStatus") { value("SHIPPING") } }
        deliver(shop.sellerToken, p.sellerOrderId).andExpect { status { isOk() }; jsonPath("$.data.sellerOrderStatus") { value("DELIVERED") } }
        confirm(user, p).andExpect {
            status { isOk() }
            jsonPath("$.data.sellerOrderStatus") { value("CONFIRMED") }
            jsonPath("$.data.orderStatus") { value("COMPLETED") }
        }
        assertEquals("CONFIRMED", sellerOrderStatus(p.sellerOrderId))
        assertEquals("COMPLETED", orderStatus(p.orderId))
        confirm(user, p).andExpect { status { isOk() } }
    }

    @Test
    fun 배송_시작_PATCH를_같은_송장으로_두_번_보내도_200() {
        val shop = shop()
        val p = paid(newUserToken(), shop, 20_000)
        ship(shop.sellerToken, p.sellerOrderId).andExpect { status { isOk() } }
        ship(shop.sellerToken, p.sellerOrderId).andExpect { status { isOk() } }
        ship(shop.sellerToken, p.sellerOrderId, "T-2").expectCode(409, "SELLER_ORDER_INVALID_STATE_TRANSITION")
    }

    @Test
    fun 구매자가_배송_중_항목을_취소하면_409() {
        val shop = shop()
        val user = newUserToken()
        val p = paid(user, shop, 20_000)
        ship(shop.sellerToken, p.sellerOrderId).andExpect { status { isOk() } }
        cancel(user, p).expectCode(409, "ORDER_ITEM_NOT_CANCELABLE")
        assertEquals("ORDERED", itemStatus(p.itemId))
    }

    @Test
    fun 다른_회원의_구매_확정은_404() {
        val shop = shop()
        val p = paid(newUserToken(), shop, 20_000)
        ship(shop.sellerToken, p.sellerOrderId)
        deliver(shop.sellerToken, p.sellerOrderId)
        confirm(newUserToken(), p).expectCode(404, "ORDER_NOT_FOUND")
    }

    @Test
    fun 다른_판매자의_배송_시작은_404이고_본문이_잘못되면_400이다() {
        val shop = shop()
        val p = paid(newUserToken(), shop, 20_000)
        ship(shop().sellerToken, p.sellerOrderId).expectCode(404, "SELLER_ORDER_NOT_FOUND")
        patch("/api/v1/seller/orders/${p.sellerOrderId}/ship", shop.sellerToken, mapOf("carrierCode" to "", "trackingNumber" to "x"))
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun 항목_취소와_배송_시작이_동시에_오면_둘_중_하나만_성공한다() {
        repeat(5) {
            val shop = shop()
            val user = newUserToken()
            val p = paid(user, shop, 20_000)

            val results = runConcurrently(2) { i ->
                val r = if (i == 0) cancel(user, p) else ship(shop.sellerToken, p.sellerOrderId)
                val res = r.andReturn().response
                res.status to json(res.contentAsString)
            }
            val (cancelStatus, cancelBody) = results[0]
            val (shipStatus, shipBody) = results[1]
            val so = sellerOrderStatus(p.sellerOrderId)
            val item = itemStatus(p.itemId)
            val detail = "so=$so item=$item cancel=$cancelStatus/$cancelBody ship=$shipStatus/$shipBody"
            when (so) {
                "SHIPPING" -> {
                    assertEquals("ORDERED", item, detail)
                    assertEquals(409, cancelStatus, detail)
                    assertEquals("ORDER_ITEM_NOT_CANCELABLE", cancelBody.get("code").asString(), detail)
                    assertEquals(200, shipStatus, detail)
                }
                "PAID", "CANCELED" -> {
                    assertTrue(item == "CANCELED" || item == "CANCEL_REQUESTED", detail)
                    assertEquals(409, shipStatus, detail)
                    assertEquals("SELLER_ORDER_INVALID_STATE_TRANSITION", shipBody.get("code").asString(), detail)
                }
                else -> throw AssertionError(detail)
            }
        }
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

    private fun json(body: String): JsonNode = objectMapper.readTree(body)
}
