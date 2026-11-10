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
import tools.jackson.databind.JsonNode
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderCancelApiTest : CommerceTestSupport() {

    @Autowired
    private lateinit var pgClient: PgClient

    private class Shop(val sellerToken: String, val productId: Long, val skuId: Long)

    private fun shop(salePrice: Long, stock: Int = 5): Shop {
        val token = newUserToken()
        approvedSeller(token)
        val productId = onSaleProduct(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)), originalPrice = salePrice, salePrice = salePrice, stock = stock)
        return Shop(token, productId, skuIdsOf(token, productId).sorted()[0])
    }

    private fun stockOf(shop: Shop): Int =
        sellerProductDetail(shop.sellerToken, shop.productId).get("skus").first { it.get("skuId").asLong() == shop.skuId }.get("stock").asInt()

    private fun salesCountOf(shop: Shop): Long = sellerProductDetail(shop.sellerToken, shop.productId).get("salesCount").asLong()

    /** 항목은 (상점, 수량). 주문을 만들고 [payKey] 로 승인한 뒤 주문 상세를 돌려준다. */
    private fun pay(user: String, items: List<Pair<Shop, Int>>, total: Long, payKey: String = "pay-${UUID.randomUUID()}"): JsonNode {
        val orderNo = json(placeOrder(user, items.map { it.first.skuId to it.second }, total).andExpect { status { isCreated() } })
            .get("data").get("orderNo").asString()
        val orderId = json(confirmPayment(user, orderNo, payKey, total).andExpect { status { isOk() } }).get("data").get("orderId").asLong()
        return orderDetail(user, orderId)
    }

    private fun orderDetail(user: String, orderId: Long): JsonNode =
        json(get("/api/v1/orders/$orderId", user).andExpect { status { isOk() } }).get("data")

    /** 판매자 주문 ID 와 그 안의 항목 ID. 판매자 주문은 판매자 ID 순이라 상점을 만든 순서와 같다. */
    private fun sellerOrderAndItem(order: JsonNode, index: Int): Pair<Long, Long> {
        val so = order.get("sellerOrders").get(index)
        return so.get("sellerOrderId").asLong() to so.get("items").get(0).get("orderItemId").asLong()
    }

    private fun cancel(user: String, orderId: Long, itemId: Long, key: String = UUID.randomUUID().toString(), reason: String = "단순 변심"): ResultActionsDsl =
        postIdem("/api/v1/orders/$orderId/items/$itemId/cancel", user, mapOf("reason" to reason), key)

    private fun ResultActionsDsl.expectCode(status: Int, code: String) {
        andExpect {
            status { isEqualTo(status) }
            jsonPath("$.code") { value(code) }
        }
    }

    private fun dataOf(result: ResultActionsDsl): JsonNode = json(result.andExpect { status { isOk() } }).get("data")

    private fun paymentRefunded(orderId: Long): Long =
        jdbcTemplate.queryForObject("select refunded_amount from payment where order_id = ?", Long::class.java, orderId)!!

    private fun paymentStatus(orderId: Long): String =
        jdbcTemplate.queryForObject("select payment_status from payment where order_id = ?", String::class.java, orderId)!!

    private fun orderStatus(orderId: Long): String =
        jdbcTemplate.queryForObject("select order_status from orders where id = ?", String::class.java, orderId)!!

    private fun itemStatus(itemId: Long): String =
        jdbcTemplate.queryForObject("select order_item_status from order_item where id = ?", String::class.java, itemId)!!

    private fun pgCancelAmounts(payKey: String): List<Long> =
        ((pgClient.find(payKey) as PgResult.Done).payment.cancels).map { it.amount }

    private fun payKeyOf(order: JsonNode): String = order.get("payment").get("paymentKey").asString()

    @Test
    fun 부분_취소를_여러_번_하면_환불_합계가_결제_금액을_넘지_않고_결제가_CANCELED로_끝난다() {
        val a = shop(20_000)
        val b = shop(20_000)
        val c = shop(20_000)
        val user = newUserToken()
        val order = pay(user, listOf(a to 1, b to 1, c to 1), 60_000)
        val orderId = order.get("orderId").asLong()
        val items = (0..2).map { sellerOrderAndItem(order, it).second }
        assertEquals(listOf(4, 4, 4), listOf(a, b, c).map(::stockOf))

        // 처음 기준(5만 원) 아래로 떨어지는 취소에서만 배송비 3,000 이 차감된다.
        assertEquals(17_000L, dataOf(cancel(user, orderId, items[0])).get("refundAmount").asLong())
        assertEquals(20_000L, dataOf(cancel(user, orderId, items[1])).get("refundAmount").asLong())
        assertEquals("PARTIAL_CANCELED", paymentStatus(orderId))
        // 마지막 취소는 차감했던 배송비를 돌려준다.
        assertEquals(23_000L, dataOf(cancel(user, orderId, items[2])).get("refundAmount").asLong())

        assertEquals(60_000L, paymentRefunded(orderId))
        assertEquals("CANCELED", paymentStatus(orderId))
        assertEquals("CANCELED", orderStatus(orderId))
        assertEquals(listOf(17_000L, 20_000L, 23_000L), pgCancelAmounts(payKeyOf(order)))
        assertEquals(listOf(5, 5, 5), listOf(a, b, c).map(::stockOf))
        assertEquals(listOf(0L, 0L, 0L), listOf(a, b, c).map(::salesCountOf))
        val detail = orderDetail(user, orderId)
        assertEquals(60_000L, detail.get("refundedAmount").asLong())
        assertEquals(0L, detail.get("shippingFeeRecovered").asLong())
    }

    @Test
    fun 무료배송_주문에서_한_판매자_항목을_취소하면_3000원이_차감되고_shippingFeeRecovered가_3000이다() {
        val b = shop(30_000)
        val c = shop(30_000)
        val user = newUserToken()
        val order = pay(user, listOf(b to 1, c to 1), 60_000)
        val orderId = order.get("orderId").asLong()
        val cItem = sellerOrderAndItem(order, 1).second

        val data = dataOf(cancel(user, orderId, cItem))

        assertEquals("CANCELED", data.get("itemStatus").asString())
        assertEquals(27_000L, data.get("refundAmount").asLong())
        assertEquals(3_000L, data.get("shippingFeeDeducted").asLong())
        val detail = orderDetail(user, orderId)
        assertEquals(3_000L, detail.get("shippingFeeRecovered").asLong())
        assertEquals(27_000L, detail.get("refundedAmount").asLong())
        assertEquals("PAID", detail.get("status").asString())
        assertEquals("CANCELED", detail.get("sellerOrders").get(1).get("status").asString())
        assertEquals("PAID", detail.get("sellerOrders").get(0).get("status").asString())
        assertEquals(listOf(27_000L), pgCancelAmounts(payKeyOf(order)))
        assertEquals(5, stockOf(c))
        assertEquals(0L, salesCountOf(c))
        assertEquals(4, stockOf(b))
        assertEquals(1L, salesCountOf(b))
    }

    @Test
    fun 구매자가_배송비를_낸_주문은_판매자_주문이_비면_배송비도_환불한다() {
        val a = shop(20_000)
        val b = shop(10_000)
        val user = newUserToken()
        val order = pay(user, listOf(a to 1, b to 1), 36_000)
        val orderId = order.get("orderId").asLong()

        assertEquals(13_000L, dataOf(cancel(user, orderId, sellerOrderAndItem(order, 1).second)).get("refundAmount").asLong())
        assertEquals(23_000L, dataOf(cancel(user, orderId, sellerOrderAndItem(order, 0).second)).get("refundAmount").asLong())

        assertEquals(36_000L, paymentRefunded(orderId))
        assertEquals("CANCELED", paymentStatus(orderId))
    }

    @Test
    fun 판매자_취소는_차감_없이_전액_환불이다() {
        val b = shop(30_000)
        val c = shop(30_000)
        val user = newUserToken()
        val order = pay(user, listOf(b to 1, c to 1), 60_000)
        val orderId = order.get("orderId").asLong()
        val (sellerOrderId, cItem) = sellerOrderAndItem(order, 1)

        // 다른 판매자는 이 판매자 주문을 취소할 수 없다.
        postIdem("/api/v1/seller/orders/$sellerOrderId/items/$cItem/cancel", b.sellerToken, mapOf("reason" to "x"))
            .expectCode(404, "SELLER_ORDER_NOT_FOUND")
        val data = dataOf(postIdem("/api/v1/seller/orders/$sellerOrderId/items/$cItem/cancel", c.sellerToken, mapOf("reason" to "재고 착오")))

        assertEquals(30_000L, data.get("refundAmount").asLong())
        assertEquals(0L, data.get("shippingFeeDeducted").asLong())
        assertEquals(0L, orderDetail(user, orderId).get("shippingFeeRecovered").asLong())
        assertEquals(30_000L, paymentRefunded(orderId))
        assertEquals(5, stockOf(c))
        assertEquals("재고 착오", orderDetail(user, orderId).get("sellerOrders").get(1).get("items").get(0).get("cancelReason").asString())
        assertEquals("SELLER", orderDetail(user, orderId).get("sellerOrders").get(1).get("items").get(0).get("cancelActor").asString())
    }

    @Test
    fun 관리자_취소는_전액_환불이다() {
        val b = shop(30_000)
        val c = shop(30_000)
        val user = newUserToken()
        val order = pay(user, listOf(b to 1, c to 1), 60_000)
        val orderId = order.get("orderId").asLong()
        val bItem = sellerOrderAndItem(order, 0).second

        postIdem("/api/v1/admin/orders/$orderId/items/$bItem/cancel", user, mapOf("reason" to "x")).andExpect { status { isForbidden() } }
        val data = dataOf(postIdem("/api/v1/admin/orders/$orderId/items/$bItem/cancel", adminToken(), mapOf("reason" to "운영 정책")))

        assertEquals(30_000L, data.get("refundAmount").asLong())
        assertEquals(0L, data.get("shippingFeeDeducted").asLong())
    }

    @Test
    fun 배송_시작된_항목_취소는_409_ORDER_ITEM_NOT_CANCELABLE() {
        val b = shop(30_000)
        val user = newUserToken()
        val order = pay(user, listOf(b to 1), 33_000)
        val orderId = order.get("orderId").asLong()
        val (sellerOrderId, item) = sellerOrderAndItem(order, 0)
        jdbcTemplate.update("update seller_order set seller_order_status = 'SHIPPING' where id = ?", sellerOrderId)

        cancel(user, orderId, item).expectCode(409, "ORDER_ITEM_NOT_CANCELABLE")

        assertEquals("ORDERED", itemStatus(item))
        assertEquals(0L, paymentRefunded(orderId))
        assertEquals(4, stockOf(b))
    }

    @Test
    fun cancelunknown_키로_결제한_주문의_취소는_CANCEL_REQUESTED로_남는다() {
        val b = shop(30_000)
        val user = newUserToken()
        val order = pay(user, listOf(b to 1), 33_000, "cancelunknown-${UUID.randomUUID()}")
        val orderId = order.get("orderId").asLong()
        val item = sellerOrderAndItem(order, 0).second

        val data = dataOf(cancel(user, orderId, item))

        assertEquals("CANCEL_REQUESTED", data.get("itemStatus").asString())
        assertEquals("CANCEL_REQUESTED", itemStatus(item))
        assertEquals(0L, paymentRefunded(orderId))
        assertEquals("APPROVED", paymentStatus(orderId))
        assertEquals(4, stockOf(b))
        assertEquals(1L, salesCountOf(b))
        // 같은 항목을 새 키로 다시 요청해도 PG 를 다시 부르지 않고 현재 상태를 돌려준다.
        assertEquals("CANCEL_REQUESTED", dataOf(cancel(user, orderId, item)).get("itemStatus").asString())
        assertEquals(1, pgCancelAmounts(payKeyOf(order)).size)
    }

    @Test
    fun cancelfail_키는_409_PAYMENT_CANCEL_FAILED이고_항목은_ORDERED다() {
        val b = shop(30_000)
        val user = newUserToken()
        val order = pay(user, listOf(b to 1), 33_000, "cancelfail-${UUID.randomUUID()}")
        val orderId = order.get("orderId").asLong()
        val item = sellerOrderAndItem(order, 0).second

        cancel(user, orderId, item).expectCode(409, "PAYMENT_CANCEL_FAILED")

        assertEquals("ORDERED", itemStatus(item))
        assertEquals(0L, paymentRefunded(orderId))
        assertEquals(4, stockOf(b))
        assertEquals(1L, salesCountOf(b))
        assertEquals("PAID", orderStatus(orderId))
    }

    @Test
    fun 같은_Idempotency_Key_재요청은_같은_응답이다() {
        val b = shop(30_000)
        val c = shop(30_000)
        val user = newUserToken()
        val order = pay(user, listOf(b to 1, c to 1), 60_000)
        val orderId = order.get("orderId").asLong()
        val item = sellerOrderAndItem(order, 1).second
        val key = UUID.randomUUID().toString()

        val first = cancel(user, orderId, item, key).andExpect { status { isOk() } }.andReturn().response.contentAsString
        val second = cancel(user, orderId, item, key).andExpect { status { isOk() } }.andReturn().response.contentAsString

        assertEquals(json(first).get("data"), json(second).get("data"))
        assertEquals(27_000L, paymentRefunded(orderId))
        assertEquals(1, pgCancelAmounts(payKeyOf(order)).size)
        // 키가 없으면 400
        post("/api/v1/orders/$orderId/items/$item/cancel", user, mapOf("reason" to "x")).expectCode(400, "IDEMPOTENCY_KEY_REQUIRED")
    }

    @Test
    fun 사유가_비어_있으면_400이고_남의_주문은_404다() {
        val b = shop(30_000)
        val user = newUserToken()
        val order = pay(user, listOf(b to 1), 33_000)
        val orderId = order.get("orderId").asLong()
        val item = sellerOrderAndItem(order, 0).second

        cancel(user, orderId, item, reason = "").expectCode(400, "INVALID_REQUEST")
        cancel(newUserToken(), orderId, item).expectCode(404, "ORDER_NOT_FOUND")
        cancel(user, orderId, 999_999L).expectCode(404, "ORDER_ITEM_NOT_FOUND")
        assertEquals("ORDERED", itemStatus(item))
    }

    @Test
    fun 같은_항목을_다른_멱등_키로_동시에_취소하면_환불과_재고_복구는_한_번이다() {
        val b = shop(30_000)
        val c = shop(30_000)
        val user = newUserToken()
        val order = pay(user, listOf(b to 1, c to 1), 60_000)
        val orderId = order.get("orderId").asLong()
        val item = sellerOrderAndItem(order, 1).second

        val results = runConcurrently(2) {
            val response = cancel(user, orderId, item).andReturn().response
            response.status to response.contentAsString
        }

        results.forEach { assertEquals(200, it.first, it.second) }
        results.forEach { assertEquals("27000", json(it.second).get("data").get("refundAmount").asString()) }
        assertEquals(1, pgCancelAmounts(payKeyOf(order)).size)
        assertEquals(27_000L, paymentRefunded(orderId))
        assertEquals("CANCELED", itemStatus(item))
        assertEquals(5, stockOf(c))
        assertEquals(0L, salesCountOf(c))
        assertEquals(3_000L, orderDetail(user, orderId).get("shippingFeeRecovered").asLong())
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
