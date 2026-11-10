package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.CommerceTestSupport
import tools.jackson.databind.JsonNode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 쿠폰·포인트 조합을 무작위로 만들어 주문하고 항목을 무작위 순서로 전부 취소해도
 * 돈(환불 합계)·포인트(잔액 = Σ 로트)·쿠폰·재고가 원래대로 돌아오는지 본다. 시드가 고정이라 실패는 재현된다.
 */
class BenefitConsistencyApiTest : CommerceTestSupport() {

    private class Shop(val sellerToken: String, val brandId: Long, val categoryId: Long, val fileIds: List<Long>)

    private class Line(val sellerToken: String, val productId: Long, val skuId: Long, val quantity: Int)

    private fun shop(): Shop {
        val token = newUserToken()
        approvedSeller(token)
        return Shop(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)))
    }

    private fun stockOf(line: Line): Int =
        sellerProductDetail(line.sellerToken, line.productId).get("skus").first { it.get("skuId").asLong() == line.skuId }.get("stock").asInt()

    private fun salesCountOf(line: Line): Long = sellerProductDetail(line.sellerToken, line.productId).get("salesCount").asLong()

    private fun statusOf(order: JsonNode, itemId: Long): String = items(order).first { it.get("orderItemId").asLong() == itemId }.get("status").asString()

    private fun items(order: JsonNode): List<JsonNode> =
        order.get("sellerOrders").flatMap { so -> so.get("items").toList() }

    private fun run(seed: Int, iterations: Int, checkEachStep: Boolean) {
        val random = Random(seed)
        val shops = listOf(shop(), shop())
        repeat(iterations) { iteration ->
            val user = newUserToken()
            val memberId = memberIdOf(user)
            val lines = ArrayList<Line>()
            val productCoupons = ArrayList<Pair<Long, Long>>()
            val log = StringBuilder("seed=$seed iteration=$iteration")
            repeat(random.nextInt(1, 5)) {
                val shop = shops[random.nextInt(shops.size)]
                val price = random.nextLong(1_000, 60_001)
                val productId = onSaleProduct(
                    shop.sellerToken, shop.brandId, shop.categoryId, shop.fileIds, originalPrice = price, salePrice = price, stock = 5,
                )
                val skuId = skuIdsOf(shop.sellerToken, productId).sorted()[0]
                val line = Line(shop.sellerToken, productId, skuId, random.nextInt(1, 4))
                lines += line
                log.append(" [sku=$skuId price=$price qty=${line.quantity}")
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
            log.append(" balance=$initialBalance maxUsable=$maxUsable points=$points")
            val label = log.toString()
            val total = json(quote(points).andExpect { status { isOk() } }).get("data").get("totalAmount").asLong()

            val placed = json(placeOrder(user, orderItems, total, productCoupons = productCoupons, cartCouponId = cartCouponId, pointsToUse = points).andExpect { status { isCreated() } }).get("data")
            val orderId = placed.get("orderId").asLong()
            if (total > 0) {
                confirmPayment(user, placed.get("orderNo").asString(), "pay-${random.nextLong()}", total).andExpect { status { isOk() } }
            }
            var order = orderDetail(user, orderId)
            assertEquals("PAID", order.get("status").asString(), label)
            assertEquals(total, order.get("totalAmount").asLong(), label)
            assertEquals(points, order.get("pointsUsed").asLong(), label)
            assertEquals(initialBalance - points, pointBalance(memberId), "$label: 주문 직후 잔액")
            assertMoneyInvariant(order, "$label 주문 직후")

            val itemIds = items(order).map { it.get("orderItemId").asLong() }.shuffled(random)
            itemIds.forEachIndexed { step, itemId ->
                cancelItem(user, orderId, itemId)
                order = orderDetail(user, orderId)
                assertEquals("CANCELED", statusOf(order, itemId), "$label: 취소 $step 항목 $itemId")
                if (checkEachStep) assertMoneyInvariant(order, "$label 취소 순서=$itemIds step=$step")
            }

            assertMoneyInvariant(order, "$label 전부 취소 후 취소 순서=$itemIds")
            val payment = order.get("payment")
            assertEquals(total, order.get("refundedAmount").asLong(), "$label: 누적 환불 취소 순서=$itemIds")
            if (total > 0) {
                assertEquals(total, payment.get("refundedAmount").asLong(), "$label: 결제 환불")
                assertEquals("CANCELED", payment.get("status").asString(), label)
            }
            assertEquals("CANCELED", order.get("status").asString(), label)
            assertEquals(points, order.get("pointsRefunded").asLong(), "$label: pointsRefunded")
            assertEquals(initialBalance, pointBalance(memberId), "$label: 최종 잔액 취소 순서=$itemIds")
            assertEquals(pointBalance(memberId), lotRemainingSum(memberId), "$label: 잔액 = Σ 로트")
            val statuses = myCouponStatuses(user)
            val usedCoupons = productCoupons.map { it.second } + listOfNotNull(cartCouponId)
            usedCoupons.forEach { assertEquals("AVAILABLE", statuses[it], "$label: 쿠폰 $it") }
            lines.forEach {
                assertEquals(5, stockOf(it), "$label: 재고 sku=${it.skuId}")
                assertEquals(0L, salesCountOf(it), "$label: salesCount product=${it.productId}")
            }
        }
    }

    @Test
    fun 무작위_쿠폰_포인트_조합을_무작위_순서로_전부_취소하면_환불_포인트_쿠폰_재고가_원래대로다() {
        run(seed = 20261110, iterations = 20, checkEachStep = false)
    }

    @Test
    fun 취소마다_결제액_환불액_남은_paidAmount_배송비_불변식이_성립한다() {
        run(seed = 20261111, iterations = 12, checkEachStep = true)
    }
}
