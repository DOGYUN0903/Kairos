package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.CommerceTestSupport
import com.dogyun.kairos.order.application.PurchaseAutoConfirmService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.ResultActionsDsl
import tools.jackson.databind.JsonNode
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestPropertySource(properties = ["kairos.order.return-window=30d"])
class OrderShippingApiTest : CommerceTestSupport() {

    @Autowired
    private lateinit var purchaseAutoConfirmService: PurchaseAutoConfirmService

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

    // ---- C3: 구매 확정 포인트 적립 ----

    private fun earnPurchaseAmounts(memberId: Long): List<Long> =
        jdbcTemplate.queryForList(
            "select amount from point_transaction where member_id = ? and transaction_type = 'EARN_PURCHASE' and ref_type = 'PURCHASE_CONFIRM' and ref_id > 0 order by id", Long::class.java, memberId,
        ).filterNotNull()

    @Test
    fun 스펙_6장_주문을_B_C_각각_확정하면_214P와_275P가_적립된다() {
        val b = shop(30_000)
        val c = shop(20_000)
        val user = newUserToken()
        val memberId = memberIdOf(user)
        val platform = claimCoupon(user, adminCouponPolicy(couponPolicyBody()))
        val seller = claimCoupon(
            user, sellerCouponPolicy(c.sellerToken, couponPolicyBody(targetType = "SELLER", discountType = "RATE", discountValue = 1_000, maxDiscount = 5_000)),
        )
        val cart = claimCoupon(
            user,
            adminCouponPolicy(couponPolicyBody(couponType = "CART", targetType = "NONE", discountType = "RATE", discountValue = 1_000, maxDiscount = 5_000, minOrderAmount = 50_000)),
        )
        grantPoints(memberId, 20_000)
        val order = paidOrder(
            user, listOf(b.skuId to 1, c.skuId to 2), 49_000,
            productCoupons = listOf(b.skuId to platform, c.skuId to seller), cartCouponId = cart, pointsToUse = 10_000,
        )
        val orderId = order.get("orderId").asLong()
        val sellerOrders = order.get("sellerOrders").associate { so ->
            so.get("items").get(0).get("skuId").asLong() to so.get("sellerOrderId").asLong()
        }
        for ((shop, soId) in listOf(b to sellerOrders.getValue(b.skuId), c to sellerOrders.getValue(c.skuId))) {
            ship(shop.sellerToken, soId).andExpect { status { isOk() } }
            deliver(shop.sellerToken, soId).andExpect { status { isOk() } }
        }
        val balanceBefore = pointBalance(memberId)

        patch("/api/v1/orders/$orderId/seller-orders/${sellerOrders.getValue(b.skuId)}/confirm", user).andExpect { status { isOk() } }
        assertEquals(listOf(214L), earnPurchaseAmounts(memberId))
        patch("/api/v1/orders/$orderId/seller-orders/${sellerOrders.getValue(c.skuId)}/confirm", user).andExpect { status { isOk() } }
        assertEquals(listOf(214L, 275L), earnPurchaseAmounts(memberId))
        // 이미 확정된 건을 다시 확정해도 더 적립되지 않는다.
        patch("/api/v1/orders/$orderId/seller-orders/${sellerOrders.getValue(c.skuId)}/confirm", user).andExpect { status { isOk() } }
        assertEquals(listOf(214L, 275L), earnPurchaseAmounts(memberId))
        assertEquals(balanceBefore + 489, pointBalance(memberId))
        assertEquals(pointBalance(memberId), lotRemainingSum(memberId))
    }

    @Test
    fun 사용자_확정과_자동_확정을_동시에_실행해도_적립은_한_번이다() {
        repeat(3) {
            val shop = shop(20_000)
            val user = newUserToken()
            val memberId = memberIdOf(user)
            val p = paid(user, shop, 20_000)
            ship(shop.sellerToken, p.sellerOrderId).andExpect { status { isOk() } }
            deliver(shop.sellerToken, p.sellerOrderId).andExpect { status { isOk() } }
            jdbcTemplate.update("update seller_order set delivered_at = ? where id = ?", java.time.LocalDateTime.now().minusDays(8), p.sellerOrderId)

            val statuses = runConcurrently(2) { i ->
                if (i == 0) confirm(user, p).andReturn().response.status else { purchaseAutoConfirmService.confirmDelivered(); 0 }
            }

            assertEquals(200, statuses[0])
            assertEquals("CONFIRMED", sellerOrderStatus(p.sellerOrderId))
            assertEquals(listOf(200L), earnPurchaseAmounts(memberId))
        }
    }

    private fun delivered(user: String, shop: Shop, price: Long = 20_000): Paid {
        val p = paid(user, shop, price)
        ship(shop.sellerToken, p.sellerOrderId).andExpect { status { isOk() } }
        deliver(shop.sellerToken, p.sellerOrderId).andExpect { status { isOk() } }
        return p
    }

    @Test
    fun 열린_반품이_있는_판매자_주문은_자동_확정_작업이_건너뛰고_반품_완료_뒤_다음_실행에서_확정한다() {
        val shop = shop()
        val user = newUserToken()
        val p = delivered(user, shop)
        jdbcTemplate.update("update seller_order set delivered_at = ? where id = ?", java.time.LocalDateTime.now().minusDays(1), p.sellerOrderId)
        val returnId = requestReturn(user, p.orderId, p.itemId)
        jdbcTemplate.update("update seller_order set delivered_at = ? where id = ?", java.time.LocalDateTime.now().minusDays(8), p.sellerOrderId)

        purchaseAutoConfirmService.confirmDelivered()
        assertEquals("DELIVERED", sellerOrderStatus(p.sellerOrderId))

        approveReturn(shop.sellerToken, returnId)
        receiveReturn(shop.sellerToken, returnId)
        // 유일한 항목이 반품 완료되면 판매자 주문은 그때 CONFIRMED 다. 다음 실행은 아무 일도 하지 않는다.
        purchaseAutoConfirmService.confirmDelivered()
        purchaseAutoConfirmService.confirmDelivered()
        assertEquals("CONFIRMED", sellerOrderStatus(p.sellerOrderId))
        assertEquals(0, pointTransactionCount(memberIdOf(user), "EARN_PURCHASE"))
    }

    @Test
    fun 거절된_반품이_있으면_다음_자동_확정_실행이_확정한다() {
        val shop = shop()
        val user = newUserToken()
        val p = delivered(user, shop)
        val returnId = requestReturn(user, p.orderId, p.itemId)
        jdbcTemplate.update("update seller_order set delivered_at = ? where id = ?", java.time.LocalDateTime.now().minusDays(8), p.sellerOrderId)
        purchaseAutoConfirmService.confirmDelivered()
        assertEquals("DELIVERED", sellerOrderStatus(p.sellerOrderId))

        rejectReturn(shop.sellerToken, returnId)
        purchaseAutoConfirmService.confirmDelivered()

        assertEquals("CONFIRMED", sellerOrderStatus(p.sellerOrderId))
    }

    @Test
    fun 열린_반품이_있는_수동_확정은_409_SELLER_ORDER_HAS_OPEN_RETURN() {
        val shop = shop()
        val user = newUserToken()
        val p = delivered(user, shop)
        requestReturn(user, p.orderId, p.itemId)

        confirm(user, p).expectCode(409, "SELLER_ORDER_HAS_OPEN_RETURN")

        assertEquals("DELIVERED", sellerOrderStatus(p.sellerOrderId))
    }

    @Test
    fun 반품_요청과_자동_확정을_동시에_실행하면_반품_성공_확정_건너뜀_또는_확정_성공_반품_RETURN_NOT_ELIGIBLE_둘_중_하나다() {
        repeat(5) {
            val shop = shop()
            val user = newUserToken()
            val p = delivered(user, shop)
            // 반품 가능 기간을 30일로 늘렸으므로(클래스 설정) 8일 전 배송 완료는 반품과 자동 확정이 둘 다 가능하다.
            jdbcTemplate.update("update seller_order set delivered_at = ? where id = ?", java.time.LocalDateTime.now().minusDays(8), p.sellerOrderId)

            val results = runConcurrently(2) { i ->
                if (i == 0) {
                    postIdem("/api/v1/orders/${p.orderId}/items/${p.itemId}/returns", user, mapOf("reason" to "CHANGE_OF_MIND"))
                        .andReturn().response.let { it.status to it.contentAsString }
                } else {
                    purchaseAutoConfirmService.confirmDelivered()
                    0 to ""
                }
            }

            val returnResponse = results[0]
            val so = sellerOrderStatus(p.sellerOrderId)
            val itemState = itemStatus(p.itemId)
            if (returnResponse.first == 201) {
                assertEquals("DELIVERED", so)
                assertEquals("RETURN_REQUESTED", itemState)
            } else {
                assertEquals(409, returnResponse.first, returnResponse.second)
                assertTrue(returnResponse.second.contains("RETURN_NOT_ELIGIBLE"), returnResponse.second)
                assertEquals("CONFIRMED", so)
                assertEquals("ORDERED", itemState)
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
