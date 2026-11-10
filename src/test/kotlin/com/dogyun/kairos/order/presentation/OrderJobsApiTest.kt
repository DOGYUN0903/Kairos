package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.CommerceTestSupport
import com.dogyun.kairos.order.application.DeliveryTrackingService
import com.dogyun.kairos.order.application.OrderExpiryService
import com.dogyun.kairos.order.application.PaymentReconciliationService
import com.dogyun.kairos.order.application.PurchaseAutoConfirmService
import com.dogyun.kairos.order.application.ReturnAutoApproveService
import com.dogyun.kairos.payment.application.PgClient
import com.dogyun.kairos.payment.application.PgResult
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrderJobsApiTest : CommerceTestSupport() {

    @Autowired private lateinit var expiryService: OrderExpiryService
    @Autowired private lateinit var reconciliationService: PaymentReconciliationService
    @Autowired private lateinit var deliveryTrackingService: DeliveryTrackingService
    @Autowired private lateinit var purchaseAutoConfirmService: PurchaseAutoConfirmService
    @Autowired private lateinit var returnAutoApproveService: ReturnAutoApproveService
    @Autowired private lateinit var pgClient: PgClient
    @Autowired private lateinit var clock: Clock
    @Autowired private lateinit var benefitReverter: com.dogyun.kairos.order.application.BenefitReverter
    @Autowired private lateinit var tx: com.dogyun.kairos.common.application.TransactionRunner

    private class Shop(val sellerToken: String, val productId: Long, val skuId: Long)

    private fun shop(salePrice: Long = 8_000, stock: Int = 5): Shop {
        val token = newUserToken()
        approvedSeller(token)
        val productId = onSaleProduct(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)), originalPrice = salePrice, salePrice = salePrice, stock = stock)
        return Shop(token, productId, skuIdsOf(token, productId).sorted()[0])
    }

    private fun stockOf(shop: Shop): Int =
        sellerProductDetail(shop.sellerToken, shop.productId).get("skus").first { it.get("skuId").asLong() == shop.skuId }.get("stock").asInt()

    private class Placed(val orderNo: String, val orderId: Long, val total: Long)

    /** 8,000원 한 개 + 배송비 3,000원. 결제는 하지 않는다. */
    private fun place(user: String, shop: Shop): Placed {
        val total = 11_000L
        val orderNo = json(placeOrder(user, listOf(shop.skuId to 1), total).andExpect { status { isCreated() } }).get("data").get("orderNo").asString()
        val orderId = jdbcTemplate.queryForObject("select id from orders where order_no = ?", Long::class.java, orderNo)!!
        return Placed(orderNo, orderId, total)
    }

    private fun key(prefix: String) = "$prefix-${UUID.randomUUID()}"

    private fun orderStatus(orderId: Long) =
        jdbcTemplate.queryForObject("select order_status from orders where id = ?", String::class.java, orderId)!!

    private fun paymentStatus(orderId: Long) =
        jdbcTemplate.queryForObject("select payment_status from payment where order_id = ?", String::class.java, orderId)!!

    private fun now(): LocalDateTime = LocalDateTime.now(clock)

    private fun pastExpiry(orderId: Long) {
        jdbcTemplate.update("update orders set expires_at = ? where id = ?", now().minusMinutes(1), orderId)
    }

    @Test
    fun 만료_시각을_과거로_바꾸고_만료_작업을_돌리면_주문_CANCELED_재고_복구() {
        val shop = shop()
        val placed = place(newUserToken(), shop)
        assertEquals(4, stockOf(shop))
        pastExpiry(placed.orderId)

        assertEquals(1, expiryService.expirePendingOrders())

        assertEquals("CANCELED", orderStatus(placed.orderId))
        assertEquals("FAILED", paymentStatus(placed.orderId))
        assertEquals(5, stockOf(shop))
        assertEquals(0, expiryService.expirePendingOrders())
    }

    @Test
    fun 만료와_승인이_동시에_오면_주문은_PAID_재고_차감_또는_CANCELED_재고_복구_둘_중_하나다() {
        repeat(8) {
            val shop = shop()
            val user = newUserToken()
            val placed = place(user, shop)
            val payKey = key("pay")
            pastExpiry(placed.orderId)

            val results = runConcurrently(2) { i ->
                if (i == 0) {
                    val res = confirmPayment(user, placed.orderNo, payKey, placed.total).andReturn().response
                    Triple(res.status, json(res.contentAsString), 0)
                } else {
                    Triple(0, null, expiryService.expirePendingOrders())
                }
            }
            val (confirmStatus, confirmBody) = results[0]
            val expired = results[1].third
            val order = orderStatus(placed.orderId)
            val payment = paymentStatus(placed.orderId)
            val pgRecorded = pgClient.find(payKey) is PgResult.Done
            val detail = "order=$order payment=$payment stock=${stockOf(shop)} confirm=$confirmStatus/$confirmBody expired=$expired pg=$pgRecorded"
            when (order) {
                "PAID" -> {
                    assertEquals(200, confirmStatus, detail)
                    assertEquals("APPROVED", payment, detail)
                    assertEquals(4, stockOf(shop), detail)
                    assertEquals(0, expired, detail)
                    assertTrue(pgRecorded, detail)
                }
                "CANCELED" -> {
                    assertEquals(409, confirmStatus, detail)
                    assertEquals("ORDER_INVALID_STATE_TRANSITION", confirmBody!!.get("code").asString(), detail)
                    assertEquals("FAILED", payment, detail)
                    assertEquals(5, stockOf(shop), detail)
                    assertEquals(1, expired, detail)
                    assertEquals(false, pgRecorded, detail)
                }
                else -> throw AssertionError(detail)
            }
        }
    }

    @Test
    fun unknown_키로_승인한_뒤_대사를_돌리면_PAID가_된다() {
        val shop = shop()
        val user = newUserToken()
        val placed = place(user, shop)
        confirmPayment(user, placed.orderNo, key("unknown"), placed.total).andExpect {
            status { isOk() }
            jsonPath("$.data.paymentStatus") { value("UNKNOWN") }
        }
        assertEquals("PENDING_PAYMENT", orderStatus(placed.orderId))

        assertEquals(1, reconciliationService.reconcileApprovals())

        assertEquals("PAID", orderStatus(placed.orderId))
        assertEquals("APPROVED", paymentStatus(placed.orderId))
        assertEquals(4, stockOf(shop))
    }

    @Test
    fun lost_키로_승인한_뒤_대사를_돌리면_CANCELED이고_재고가_돌아온다() {
        val shop = shop()
        val user = newUserToken()
        val placed = place(user, shop)
        confirmPayment(user, placed.orderNo, key("lost"), placed.total).andExpect { status { isOk() } }
        assertEquals(4, stockOf(shop))

        assertEquals(1, reconciliationService.reconcileApprovals())

        assertEquals("CANCELED", orderStatus(placed.orderId))
        assertEquals("FAILED", paymentStatus(placed.orderId))
        assertEquals(5, stockOf(shop))
    }

    @Test
    fun 닫힌_주문의_결제가_PG에_남아_있으면_재취소_작업이_같은_멱등_키로_전액_취소한다() {
        val shop = shop()
        val user = newUserToken()
        val placed = place(user, shop)
        val payKey = key("unknown")
        confirmPayment(user, placed.orderNo, payKey, placed.total).andExpect { status { isOk() } }
        // 승인 확인이 늦어지는 사이 주문이 닫혔다고 가정한다(결제는 UNKNOWN 으로 남음).
        jdbcTemplate.update("update orders set order_status = 'CANCELED' where id = ?", placed.orderId)

        assertEquals(1, reconciliationService.revoidClosedOrders())

        assertEquals("CANCELED", paymentStatus(placed.orderId))
        val pg = (pgClient.find(payKey) as PgResult.Done).payment
        assertEquals(0L, pg.balanceAmount)
        assertEquals(0, reconciliationService.revoidClosedOrders())
    }

    /** 주문을 결제하고(키 접두사로 Mock PG 동작 선택) 항목 하나를 취소 요청한다. orderId 와 itemId 를 돌려준다. */
    private fun payAndCancel(shop: Shop, payKey: String): Triple<Long, Long, String> {
        val user = newUserToken()
        val placed = place(user, shop)
        confirmPayment(user, placed.orderNo, payKey, placed.total).andExpect { status { isOk() } }
        val itemId = jdbcTemplate.queryForObject(
            "select i.id from order_item i join seller_order s on s.id = i.seller_order_id where s.order_id = ?", Long::class.java, placed.orderId,
        )!!
        postIdem("/api/v1/orders/${placed.orderId}/items/$itemId/cancel", user, mapOf("reason" to "변심"))
            .andExpect { status { isOk() }; jsonPath("$.data.itemStatus") { value("CANCEL_REQUESTED") } }
        return Triple(placed.orderId, itemId, payKey)
    }

    private fun itemStatus(itemId: Long) =
        jdbcTemplate.queryForObject("select order_item_status from order_item where id = ?", String::class.java, itemId)!!

    private fun staleItem(itemId: Long) {
        jdbcTemplate.update("update order_item set modified_at = ? where id = ?", now().minusMinutes(10), itemId)
    }

    @Test
    fun cancelunknown_키_주문의_취소_뒤_대사를_돌리면_CANCELED로_확정되고_환불이_기록된다() {
        val shop = shop()
        val (orderId, itemId, payKey) = payAndCancel(shop, key("cancelunknown"))
        assertEquals(0, reconciliationService.reconcileCancels()) // 아직 정체 시간(1분)이 지나지 않았다.
        staleItem(itemId)

        assertEquals(1, reconciliationService.reconcileCancels())

        assertEquals("CANCELED", itemStatus(itemId))
        assertEquals(11_000L, jdbcTemplate.queryForObject("select refunded_amount from payment where order_id = ?", Long::class.java, orderId))
        assertEquals("CANCELED", orderStatus(orderId))
        assertEquals(5, stockOf(shop))
        assertEquals(1, (pgClient.find(payKey) as PgResult.Done).payment.cancels.size)
    }

    @Test
    fun cancellost_키_주문의_취소_뒤_대사를_돌리면_PG_취소를_다시_호출해_확정한다() {
        val shop = shop()
        val (orderId, itemId, payKey) = payAndCancel(shop, key("cancellost"))
        assertTrue((pgClient.find(payKey) as PgResult.Done).payment.cancels.isEmpty())
        staleItem(itemId)

        assertEquals(1, reconciliationService.reconcileCancels())

        assertEquals("CANCELED", itemStatus(itemId))
        assertEquals(11_000L, jdbcTemplate.queryForObject("select refunded_amount from payment where order_id = ?", Long::class.java, orderId))
        assertEquals(5, stockOf(shop))
        val cancels = (pgClient.find(payKey) as PgResult.Done).payment.cancels
        assertEquals(listOf("orderItem:$itemId"), cancels.map { it.reason })
    }

    private fun sellerOrderIdOf(orderId: Long) =
        jdbcTemplate.queryForObject("select id from seller_order where order_id = ?", Long::class.java, orderId)!!

    private fun sellerOrderStatus(id: Long) =
        jdbcTemplate.queryForObject("select seller_order_status from seller_order where id = ?", String::class.java, id)!!

    private fun paidOrderWithSeller(): Triple<Shop, Long, Long> {
        val shop = shop()
        val order = paidOrder(newUserToken(), listOf(shop.skuId to 1), 11_000)
        val orderId = order.get("orderId").asLong()
        return Triple(shop, orderId, sellerOrderIdOf(orderId))
    }

    private fun ship(shop: Shop, sellerOrderId: Long, tracking: String) {
        patch("/api/v1/seller/orders/$sellerOrderId/ship", shop.sellerToken, mapOf("carrierCode" to "04", "trackingNumber" to tracking))
            .andExpect { status { isOk() } }
    }

    @Test
    fun 송장이_D로_끝나는_SHIPPING_판매자_주문은_배송_조회_작업으로_DELIVERED가_된다() {
        val (shop, _, deliveredSo) = paidOrderWithSeller()
        ship(shop, deliveredSo, "TRK-1-D")
        val (shop2, _, transitSo) = paidOrderWithSeller()
        ship(shop2, transitSo, "TRK-2")

        assertEquals(1, deliveryTrackingService.pollShipping())

        assertEquals("DELIVERED", sellerOrderStatus(deliveredSo))
        assertEquals("SHIPPING", sellerOrderStatus(transitSo))
    }

    @Test
    fun delivered_at을_8일_전으로_바꾸면_자동_확정된다() {
        val (shop, orderId, soId) = paidOrderWithSeller()
        ship(shop, soId, "TRK-3")
        patch("/api/v1/seller/orders/$soId/deliver", shop.sellerToken).andExpect { status { isOk() } }
        assertEquals(0, purchaseAutoConfirmService.confirmDelivered())
        jdbcTemplate.update("update seller_order set delivered_at = ? where id = ?", now().minusDays(8), soId)

        assertEquals(1, purchaseAutoConfirmService.confirmDelivered())

        assertEquals("CONFIRMED", sellerOrderStatus(soId))
        assertEquals("COMPLETED", orderStatus(orderId))
    }

    // ---- C3: 결제 전 닫힘의 쿠폰·포인트 되돌림 ----

    private class BenefitPlaced(val user: String, val memberId: Long, val couponId: Long, val placed: Placed)

    /** 8,000원 한 개 + 배송비 3,000원에 2,000원 쿠폰과 포인트 1,000(잔액 10,000) → 8,000원. 결제는 하지 않는다. */
    private fun placeWithBenefits(): BenefitPlaced {
        val shop = shop()
        val user = newUserToken()
        val memberId = memberIdOf(user)
        grantPoints(memberId, 10_000)
        val couponId = claimCoupon(user, adminCouponPolicy(couponPolicyBody()))
        val total = 8_000L
        val orderNo = json(
            placeOrder(user, listOf(shop.skuId to 1), total, productCoupons = listOf(shop.skuId to couponId), pointsToUse = 1_000)
                .andExpect { status { isCreated() } },
        ).get("data").get("orderNo").asString()
        val orderId = jdbcTemplate.queryForObject("select id from orders where order_no = ?", Long::class.java, orderNo)!!
        assertEquals(9_000L, pointBalance(memberId))
        assertEquals("USED", myCouponStatuses(user).getValue(couponId))
        return BenefitPlaced(user, memberId, couponId, Placed(orderNo, orderId, total))
    }

    private fun assertBenefitsReturned(f: BenefitPlaced) {
        assertEquals("CANCELED", orderStatus(f.placed.orderId))
        assertEquals("AVAILABLE", myCouponStatuses(f.user).getValue(f.couponId))
        assertEquals(10_000L, pointBalance(f.memberId))
        assertEquals(pointBalance(f.memberId), lotRemainingSum(f.memberId))
        assertEquals(1, pointTransactionCount(f.memberId, "REFUND"))
    }

    @Test
    fun 결제_만료되면_쿠폰과_포인트가_돌아온다() {
        val f = placeWithBenefits()
        pastExpiry(f.placed.orderId)

        assertEquals(1, expiryService.expirePendingOrders())

        assertBenefitsReturned(f)
    }

    @Test
    fun fail_키_승인_거절에서_쿠폰과_포인트가_돌아온다() {
        val f = placeWithBenefits()

        confirmPayment(f.user, f.placed.orderNo, key("fail"), f.placed.total).andExpect { status { isEqualTo(402) } }

        assertBenefitsReturned(f)
    }

    @Test
    fun 금액_불일치_void에서_쿠폰과_포인트가_돌아온다() {
        val f = placeWithBenefits()
        val payKey = key("pay")
        // PG 에 다른 금액(1,000원)으로 승인된 결제가 이미 있게 해 승인 응답 금액이 주문과 다르게 한다.
        pgClient.confirm(payKey, f.placed.orderNo, 1_000)

        confirmPayment(f.user, f.placed.orderNo, payKey, f.placed.total).andExpect { status { isEqualTo(402) } }

        assertEquals("CANCELED", ((pgClient.find(payKey) as PgResult.Done).payment.status).name)
        assertBenefitsReturned(f)
    }

    @Test
    fun 같은_주문의_되돌림을_두_번_실행해도_원장_REFUND는_한_건이다() {
        val f = placeWithBenefits()
        pastExpiry(f.placed.orderId)

        assertEquals(1, expiryService.expirePendingOrders())
        assertEquals(0, expiryService.expirePendingOrders())
        // 주문 상태 확인을 건너뛰고 되돌림 자체를 한 번 더 실행해도 원장 UNIQUE 가 막는다.
        tx.run { benefitReverter.revertAll(f.placed.orderId, f.memberId, listOf(f.couponId), 1_000) }

        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "select count(*) from point_transaction where transaction_type = 'REFUND' and ref_type = 'ORDER' and ref_id = ?",
                Int::class.java, f.placed.orderId,
            ),
        )
        assertBenefitsReturned(f)
    }

    // ---- C4: 반품 자동 승인 ----

    /** 30,000원 한 개 배송 완료 주문에 반품을 요청하고 returnId 를 돌려준다. */
    private fun requestedReturn(): Pair<Shop, Long> {
        val shop = shop(30_000)
        val buyer = newUserToken()
        val order = deliveredOrder(buyer, shop.sellerToken, listOf(shop.skuId to 1), 33_000)
        val itemId = order.get("sellerOrders").flatMap { it.get("items").toList() }[0].get("orderItemId").asLong()
        return shop to requestReturn(buyer, order.get("orderId").asLong(), itemId)
    }

    private fun returnRow(returnId: Long) = jdbcTemplate.queryForMap("select return_status, decided_by, decided_at from return_request where id = ?", returnId)

    private fun backdateReturn(returnId: Long, days: Long) {
        jdbcTemplate.update("update return_request set requested_at = ? where id = ?", now().minusDays(days), returnId)
    }

    @Test
    fun requested_at을_4일_전으로_바꾸고_작업을_돌리면_APPROVED_decidedBy_SYSTEM이다() {
        val (_, returnId) = requestedReturn()
        assertEquals(0, returnAutoApproveService.approveStale())
        backdateReturn(returnId, 4)

        assertEquals(1, returnAutoApproveService.approveStale())

        val row = returnRow(returnId)
        assertEquals("APPROVED", row["return_status"])
        assertEquals("SYSTEM", row["decided_by"])
        assertEquals(0, returnAutoApproveService.approveStale())
    }

    @Test
    fun 판매자_승인과_자동_승인을_동시에_실행해도_decided_at은_한_번만_기록된다() {
        repeat(5) {
            val (shop, returnId) = requestedReturn()
            backdateReturn(returnId, 4)

            runConcurrently(2) { i ->
                if (i == 0) patch("/api/v1/seller/returns/$returnId/approve", shop.sellerToken).andReturn().response.status
                else returnAutoApproveService.approveStale()
            }

            val row = returnRow(returnId)
            assertEquals("APPROVED", row["return_status"])
            assertTrue(row["decided_by"] == "SELLER" || row["decided_by"] == "SYSTEM", "decided_by=${row["decided_by"]}")
            assertTrue(row["decided_at"] != null)
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
