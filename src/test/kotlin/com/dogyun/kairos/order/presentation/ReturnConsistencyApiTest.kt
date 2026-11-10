package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.CommerceTestSupport
import tools.jackson.databind.JsonNode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 쿠폰·포인트 조합 주문을 무작위 순서의 반품·철회·거절·승인·수거·취소로 처리해도 돈 불변식과 발송 구간 회수 상한(3,000)이 성립하는지,
 * 전부 비면 환불·포인트·쿠폰이 규칙대로인지 본다. 시드가 고정이라 실패는 재현된다.
 */
class ReturnConsistencyApiTest : CommerceTestSupport() {

    private class Shop(val sellerToken: String, val brandId: Long, val categoryId: Long, val fileIds: List<Long>)

    private class Line(val sellerToken: String, val skuId: Long, val quantity: Int)

    private enum class Op { REQUEST, WITHDRAW, REJECT, APPROVE, RECEIVE, ADMIN_CANCEL, CANCEL }

    private fun shop(): Shop {
        val token = newUserToken()
        approvedSeller(token)
        return Shop(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)))
    }

    private fun items(order: JsonNode): List<JsonNode> = order.get("sellerOrders").flatMap { so -> so.get("items").toList() }

    private fun statusOf(order: JsonNode, itemId: Long): String =
        items(order).first { it.get("orderItemId").asLong() == itemId }.get("status").asString()

    private fun completedFees(order: JsonNode): Long = items(order).sumOf {
        val r = it.get("returnRequest")
        if (r != null && !r.isNull && r.get("status").asString() == "COMPLETED") r.get("returnFee").asLong() else 0L
    }

    private fun randomReason(random: Random) = listOf("CHANGE_OF_MIND", "DEFECTIVE", "WRONG_DELIVERY")[random.nextInt(3)]

    private class Placed(val user: String, val memberId: Long, val orderId: Long, val total: Long, val points: Long, val initialBalance: Long, val cartCouponId: Long?, val lines: List<Line>, val label: String)

    private fun place(random: Random, shops: List<Shop>, iteration: Int): Placed {
        val user = newUserToken()
        val memberId = memberIdOf(user)
        val lines = ArrayList<Line>()
        val productCoupons = ArrayList<Pair<Long, Long>>()
        val log = StringBuilder("iteration=$iteration")
        val chosen = if (random.nextBoolean()) listOf(shops[random.nextInt(shops.size)]) else shops
        repeat(random.nextInt(2, 5)) { n ->
            val shop = if (n < chosen.size) chosen[n] else chosen[random.nextInt(chosen.size)]
            val price = random.nextLong(1_000, 60_001)
            val productId = onSaleProduct(shop.sellerToken, shop.brandId, shop.categoryId, shop.fileIds, originalPrice = price, salePrice = price, stock = 5)
            val skuId = skuIdsOf(shop.sellerToken, productId).sorted()[0]
            lines += Line(shop.sellerToken, skuId, random.nextInt(1, 3))
            log.append(" [sku=$skuId price=$price qty=${lines.last().quantity}")
            if (random.nextBoolean()) {
                val body = if (random.nextBoolean()) {
                    val v = random.nextLong(500, 5_001)
                    log.append(" fixedCoupon=$v")
                    couponPolicyBody(discountType = "FIXED", discountValue = v)
                } else {
                    val bp = random.nextLong(500, 3_001)
                    val max = random.nextLong(1_000, 10_001)
                    log.append(" rateCoupon=${bp}bp/max$max")
                    couponPolicyBody(discountType = "RATE", discountValue = bp, maxDiscount = max)
                }
                productCoupons += skuId to claimCoupon(user, adminCouponPolicy(body))
            }
            log.append("]")
        }
        var cartCouponId: Long? = null
        if (random.nextBoolean()) {
            val body = if (random.nextBoolean()) {
                val v = random.nextLong(500, 5_001)
                log.append(" cartFixed=$v")
                couponPolicyBody(couponType = "CART", targetType = "NONE", discountType = "FIXED", discountValue = v)
            } else {
                val bp = random.nextLong(500, 3_001)
                val max = random.nextLong(1_000, 10_001)
                log.append(" cartRate=${bp}bp/max$max")
                couponPolicyBody(couponType = "CART", targetType = "NONE", discountType = "RATE", discountValue = bp, maxDiscount = max)
            }
            cartCouponId = claimCoupon(user, adminCouponPolicy(body))
        }
        val initialBalance = random.nextLong(1_000, 50_001)
        grantPoints(memberId, initialBalance)
        val orderItems = lines.map { it.skuId to it.quantity }
        fun quote(points: Long) = post(
            "/api/v1/orders/quote", user,
            mapOf(
                "items" to orderItems.map { mapOf("skuId" to it.first, "quantity" to it.second) },
                "productCoupons" to productCoupons.map { mapOf("skuId" to it.first, "couponId" to it.second) },
                "cartCouponId" to cartCouponId,
                "pointsToUse" to points,
            ),
        )
        val maxUsable = json(quote(0).andExpect { status { isOk() } }).get("data").get("maxUsablePoints").asLong()
        val points = if (maxUsable >= 10 && random.nextBoolean()) random.nextLong(1, maxUsable / 10 + 1) * 10 else 0L
        log.append(" balance=$initialBalance points=$points")
        val total = json(quote(points).andExpect { status { isOk() } }).get("data").get("totalAmount").asLong()
        val placed = json(
            placeOrder(user, orderItems, total, productCoupons = productCoupons, cartCouponId = cartCouponId, pointsToUse = points).andExpect { status { isCreated() } },
        ).get("data")
        if (total > 0) confirmPayment(user, placed.get("orderNo").asString(), "pay-${java.util.UUID.randomUUID()}", total).andExpect { status { isOk() } }
        return Placed(user, memberId, placed.get("orderId").asLong(), total, points, initialBalance, cartCouponId, lines, log.toString())
    }

    private fun sellerTokenOf(p: Placed, item: JsonNode): String = p.lines.first { it.skuId == item.get("skuId").asLong() }.sellerToken

    /** 판매자 주문을 배송 완료까지 보낸다. [leavePaid] 면 PAID 로 둔다(그 항목은 취소만 가능). 남겨 둔 판매자 주문 id 집합을 돌려준다. */
    private fun deliver(p: Placed, random: Random, allowLeavePaid: Boolean): Set<Long> {
        val order = orderDetail(p.user, p.orderId)
        val left = HashSet<Long>()
        order.get("sellerOrders").forEach { so ->
            val soId = so.get("sellerOrderId").asLong()
            if (allowLeavePaid && random.nextInt(4) == 0) {
                left += soId
                return@forEach
            }
            val token = sellerTokenOf(p, so.get("items").get(0))
            patch("/api/v1/seller/orders/$soId/ship", token, mapOf("carrierCode" to "04", "trackingNumber" to "T-$soId-D")).andExpect { status { isOk() } }
            patch("/api/v1/seller/orders/$soId/deliver", token).andExpect { status { isOk() } }
        }
        return left
    }

    private val scripts = listOf(
        emptyList(),
        listOf(Op.REQUEST),
        listOf(Op.REQUEST, Op.WITHDRAW),
        listOf(Op.REQUEST, Op.REJECT),
        listOf(Op.REQUEST, Op.APPROVE),
        listOf(Op.REQUEST, Op.APPROVE, Op.ADMIN_CANCEL),
        listOf(Op.REQUEST, Op.APPROVE, Op.RECEIVE),
        listOf(Op.REQUEST, Op.WITHDRAW, Op.REQUEST, Op.APPROVE, Op.RECEIVE),
    )

    private val fullScripts = listOf(
        listOf(Op.REQUEST, Op.APPROVE, Op.RECEIVE),
        listOf(Op.REQUEST, Op.WITHDRAW, Op.REQUEST, Op.APPROVE, Op.RECEIVE),
    )

    private fun run(seed: Int, iterations: Int, full: Boolean) {
        val random = Random(seed)
        val shops = listOf(shop(), shop())
        repeat(iterations) { iteration ->
            val p = place(random, shops, iteration)
            var order = orderDetail(p.user, p.orderId)
            assertEquals("PAID", order.get("status").asString(), p.label)
            val leftPaid = deliver(p, random, allowLeavePaid = true)
            order = orderDetail(p.user, p.orderId)
            assertMoneyInvariant(order, "${p.label} 배송 뒤")

            class Work(val itemId: Long, val token: String, val reason: String, val queue: ArrayDeque<Op>, var returnId: Long? = null)
            val work = ArrayList<Work>()
            order.get("sellerOrders").forEach { so ->
                val soId = so.get("sellerOrderId").asLong()
                so.get("items").forEach { item ->
                    val ops = if (soId in leftPaid) {
                        if (full || random.nextBoolean()) listOf(Op.CANCEL) else emptyList()
                    } else if (full) fullScripts[random.nextInt(fullScripts.size)] else scripts[random.nextInt(scripts.size)]
                    work += Work(item.get("orderItemId").asLong(), sellerTokenOf(p, item), randomReason(random), ArrayDeque(ops))
                }
            }
            val trace = StringBuilder(p.label)
            while (work.any { it.queue.isNotEmpty() }) {
                val w = work.filter { it.queue.isNotEmpty() }.let { it[random.nextInt(it.size)] }
                val op = w.queue.removeFirst()
                trace.append(" | item=${w.itemId}:$op")
                when (op) {
                    Op.REQUEST -> w.returnId = requestReturn(p.user, p.orderId, w.itemId, w.reason)
                    Op.WITHDRAW -> delete("/api/v1/returns/${w.returnId}", p.user).andExpect { status { isOk() } }
                    Op.REJECT -> rejectReturn(w.token, w.returnId!!)
                    Op.APPROVE -> approveReturn(w.token, w.returnId!!)
                    Op.RECEIVE -> receiveReturn(w.token, w.returnId!!)
                    Op.ADMIN_CANCEL -> patch("/api/v1/admin/returns/${w.returnId}/cancel", adminToken()).andExpect { status { isOk() } }
                    Op.CANCEL -> cancelItem(p.user, p.orderId, w.itemId)
                }
                order = orderDetail(p.user, p.orderId)
                assertMoneyInvariant(order, trace.toString())
                val recovered = order.get("shippingFeeRecovered").asLong()
                assertTrue(recovered in 0..3_000, "$trace: shippingFeeRecovered=$recovered")
            }

            val payment = order.get("payment")
            val refunded = order.get("refundedAmount").asLong()
            if (p.total > 0) {
                assertEquals(refunded, payment.get("refundedAmount").asLong(), "$trace: 결제 환불")
                assertEquals(refunded == p.total, payment.get("status").asString() == "CANCELED", "$trace: 결제 CANCELED 는 전액 환불일 때만")
            }
            assertEquals(p.memberId.let { pointBalance(it) }, lotRemainingSum(p.memberId), "$trace: 잔액 = Σ 로트")
            val emptied = items(order).all { it.get("status").asString() in setOf("CANCELED", "RETURNED") }
            if (full) assertTrue(emptied, "$trace: 전부 비어야 한다")
            if (emptied) {
                // 변심 반품만 남은 판매자 주문의 구매자 배송비는 환불되지 않고 남는다(remainingBuyerShippingFee).
                assertEquals(
                    p.total - completedFees(order) - order.get("shippingFeeRecovered").asLong() - order.get("remainingBuyerShippingFee").asLong(),
                    refunded, "$trace: 전부 비면 환불",
                )
                assertEquals(p.points, order.get("pointsRefunded").asLong(), "$trace: pointsRefunded")
                assertEquals(p.initialBalance, pointBalance(p.memberId), "$trace: 최종 잔액")
                p.cartCouponId?.let { assertEquals("AVAILABLE", myCouponStatuses(p.user)[it], "$trace: 장바구니 쿠폰") }
            }
        }
    }

    @Test
    fun 무작위_반품_취소_순서에서_돈_불변식과_발송_회수_상한이_성립한다() {
        run(seed = 20261110, iterations = 20, full = false)
    }

    @Test
    fun 전부_반품_취소로_비면_환불_포인트_쿠폰이_규칙대로다() {
        run(seed = 20261112, iterations = 20, full = true)
    }
}
