package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.CommerceTestSupport
import org.springframework.test.web.servlet.ResultActionsDsl
import tools.jackson.databind.JsonNode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SellerReturnApiTest : CommerceTestSupport() {

    private class Shop(val sellerToken: String, val brandId: Long, val categoryId: Long, val fileIds: List<Long>)

    private class Product(val productId: Long, val skuId: Long)

    private fun shop(): Shop {
        val token = newUserToken()
        approvedSeller(token)
        return Shop(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)))
    }

    private fun Shop.product(price: Long, stock: Int = 5): Product {
        val productId = onSaleProduct(sellerToken, brandId, categoryId, fileIds, originalPrice = price, salePrice = price, stock = stock)
        return Product(productId, skuIdsOf(sellerToken, productId).sorted()[0])
    }

    private fun items(order: JsonNode): List<JsonNode> = order.get("sellerOrders").flatMap { it.get("items").toList() }

    private fun itemByPaid(order: JsonNode, paid: Long): Long = items(order).first { it.get("paidAmount").asLong() == paid }.get("orderItemId").asLong()

    private fun itemStatus(order: JsonNode, itemId: Long): String =
        items(order).first { it.get("orderItemId").asLong() == itemId }.get("status").asString()

    private fun ResultActionsDsl.expectCode(status: Int, code: String) {
        andExpect {
            status { isEqualTo(status) }
            jsonPath("$.code") { value(code) }
        }
    }

    /** 30,000원 한 개 짜리 배송 완료 주문(배송비 3,000 포함 33,000)과 반품 요청. */
    private class Requested(val buyer: String, val orderId: Long, val itemId: Long, val returnId: Long)

    private fun requested(shop: Shop, reason: String = "CHANGE_OF_MIND"): Requested {
        val product = shop.product(30_000)
        val buyer = newUserToken()
        val order = deliveredOrder(buyer, shop.sellerToken, listOf(product.skuId to 1), 33_000)
        val itemId = items(order)[0].get("orderItemId").asLong()
        val orderId = order.get("orderId").asLong()
        return Requested(buyer, orderId, itemId, requestReturn(buyer, orderId, itemId, reason))
    }

    @Test
    fun 승인_거절_수거는_멱등이고_두_번째_호출은_현재_상태를_돌려준다() {
        val shop = shop()
        val a = requested(shop)
        assertEquals("APPROVED", approveReturn(shop.sellerToken, a.returnId).get("status").asString())
        assertEquals("APPROVED", approveReturn(shop.sellerToken, a.returnId).get("status").asString())
        val first = receiveReturn(shop.sellerToken, a.returnId)
        val second = receiveReturn(shop.sellerToken, a.returnId)
        assertEquals("COMPLETED", first.get("status").asString())
        assertEquals("COMPLETED", second.get("status").asString())
        assertEquals(first.get("refundAmount").asLong(), second.get("refundAmount").asLong())
        assertEquals(first.get("refundAmount").asLong(), orderDetail(a.buyer, a.orderId).get("refundedAmount").asLong())

        val b = requested(shop)
        assertEquals("REJECTED", rejectReturn(shop.sellerToken, b.returnId).get("status").asString())
        assertEquals("REJECTED", rejectReturn(shop.sellerToken, b.returnId).get("status").asString())
    }

    @Test
    fun 거절은_rejectReason이_없으면_400_RETURN_INVALID이고_항목은_ORDERED로_돌아온다() {
        val shop = shop()
        val r = requested(shop)

        patch("/api/v1/seller/returns/${r.returnId}/reject", shop.sellerToken, emptyMap<String, Any?>()).expectCode(400, "RETURN_INVALID")
        patch("/api/v1/seller/returns/${r.returnId}/reject", shop.sellerToken, mapOf("rejectReason" to "  ")).expectCode(400, "RETURN_INVALID")
        assertEquals("RETURN_REQUESTED", itemStatus(orderDetail(r.buyer, r.orderId), r.itemId))

        val rejected = rejectReturn(shop.sellerToken, r.returnId, "사용 흔적이 있습니다")
        assertEquals("사용 흔적이 있습니다", rejected.get("rejectReason").asString())
        val detail = orderDetail(r.buyer, r.orderId)
        assertEquals("ORDERED", itemStatus(detail, r.itemId))
        assertEquals(false, detail.get("sellerOrders").get(0).get("hasOpenReturn").asBoolean())
    }

    @Test
    fun 거절_뒤_구매자_재요청은_409_RETURN_ALREADY_REQUESTED() {
        val shop = shop()
        val r = requested(shop)
        rejectReturn(shop.sellerToken, r.returnId)

        postIdem("/api/v1/orders/${r.orderId}/items/${r.itemId}/returns", r.buyer, mapOf("reason" to "CHANGE_OF_MIND"), UUID.randomUUID().toString())
            .expectCode(409, "RETURN_ALREADY_REQUESTED")
    }

    @Test
    fun 다른_판매자의_반품은_404() {
        val shop = shop()
        val other = shop()
        val r = requested(shop)

        get("/api/v1/seller/returns/${r.returnId}", other.sellerToken).expectCode(404, "RETURN_NOT_FOUND")
        patch("/api/v1/seller/returns/${r.returnId}/approve", other.sellerToken).expectCode(404, "RETURN_NOT_FOUND")
        patch("/api/v1/seller/returns/${r.returnId}/reject", other.sellerToken, mapOf("rejectReason" to "x")).expectCode(404, "RETURN_NOT_FOUND")
        patch("/api/v1/seller/returns/${r.returnId}/receive", other.sellerToken).expectCode(404, "RETURN_NOT_FOUND")
        patch("/api/v1/seller/returns/999999/approve", shop.sellerToken).expectCode(404, "RETURN_NOT_FOUND")
        assertEquals("REQUESTED", json(get("/api/v1/seller/returns/${r.returnId}", shop.sellerToken).andExpect { status { isOk() } }).get("data").get("status").asString())
    }

    @Test
    fun 판매자_반품_목록은_status_필터가_된다() {
        val shop = shop()
        val approved = requested(shop)
        approveReturn(shop.sellerToken, approved.returnId)
        val pending = requested(shop)
        val other = shop()
        requested(other)

        val all = json(get("/api/v1/seller/returns", shop.sellerToken).andExpect { status { isOk() } }).get("data")
        assertEquals(2, all.get("totalElements").asInt())
        val onlyApproved = json(get("/api/v1/seller/returns?status=APPROVED", shop.sellerToken).andExpect { status { isOk() } }).get("data")
        assertEquals(1, onlyApproved.get("totalElements").asInt())
        assertEquals(approved.returnId, onlyApproved.get("content").get(0).get("returnId").asLong())
        val onlyRequested = json(get("/api/v1/seller/returns?status=REQUESTED", shop.sellerToken).andExpect { status { isOk() } }).get("data")
        assertEquals(listOf(pending.returnId), onlyRequested.get("content").toList().map { it.get("returnId").asLong() })
        get("/api/v1/seller/returns?status=NOPE", shop.sellerToken).andExpect { status { isBadRequest() } }
    }

    @Test
    fun 예시A를_API로_수행하면_환불_14000_37000_결제_PARTIAL_CANCELED_판매자_주문_CONFIRMED_allItemsReturned_true_주문_COMPLETED_적립_0P() {
        val shop = shop()
        val a1 = shop.product(40_000)
        val a2 = shop.product(20_000)
        val buyer = newUserToken()
        val order = deliveredOrder(buyer, shop.sellerToken, listOf(a1.skuId to 1, a2.skuId to 1), 60_000)
        val orderId = order.get("orderId").asLong()
        val a1Item = itemByPaid(order, 40_000)
        val a2Item = itemByPaid(order, 20_000)

        val r2 = completedReturn(buyer, shop.sellerToken, orderId, a2Item)
        assertEquals("COMPLETED", r2.get("status").asString())
        assertEquals(14_000, r2.get("refundAmount").asLong())
        val mid = orderDetail(buyer, orderId)
        assertEquals(3_000, mid.get("shippingFeeRecovered").asLong())
        assertEquals("DELIVERED", mid.get("sellerOrders").get(0).get("status").asString())
        assertMoneyInvariant(mid, "a2 반품 뒤")

        val r1 = completedReturn(buyer, shop.sellerToken, orderId, a1Item)
        assertEquals(37_000, r1.get("refundAmount").asLong())
        val end = orderDetail(buyer, orderId)
        assertEquals(51_000, end.get("refundedAmount").asLong())
        assertEquals("PARTIAL_CANCELED", paymentStatusOf(orderId))
        assertEquals("PARTIAL_CANCELED", end.get("payment").get("status").asString())
        val so = end.get("sellerOrders").get(0)
        assertEquals("CONFIRMED", so.get("status").asString())
        assertEquals(true, so.get("allItemsReturned").asBoolean())
        assertEquals("COMPLETED", end.get("status").asString())
        assertEquals(0, pointTransactionCount(memberIdOf(buyer), "EARN_PURCHASE"))
        assertMoneyInvariant(end, "a1 반품 뒤")
    }

    @Test
    fun 예시B를_API로_수행하면_환불_33000_결제_CANCELED_정산용_shippingFeeRefund_3000() {
        val shop = shop()
        val b1 = shop.product(30_000)
        val buyer = newUserToken()
        val order = deliveredOrder(buyer, shop.sellerToken, listOf(b1.skuId to 1), 33_000)
        val orderId = order.get("orderId").asLong()

        val result = completedReturn(buyer, shop.sellerToken, orderId, items(order)[0].get("orderItemId").asLong(), "DEFECTIVE")

        assertEquals(33_000, result.get("refundAmount").asLong())
        assertEquals(3_000, result.get("shippingFeeRefund").asLong())
        assertEquals(3_000, result.get("sellerBorneFee").asLong())
        assertEquals(0, result.get("returnFee").asLong())
        assertEquals("CANCELED", paymentStatusOf(orderId))
        val end = orderDetail(buyer, orderId)
        assertEquals("CONFIRMED", end.get("sellerOrders").get(0).get("status").asString())
        assertMoneyInvariant(end, "예시 B")
    }

    @Test
    fun 예시D_포인트_판매자_쿠폰_항목_불량_반품은_현금_38000_포인트_3000_쿠폰_AVAILABLE() {
        val shop = shop()
        val d1 = shop.product(50_000)
        val buyer = newUserToken()
        val memberId = memberIdOf(buyer)
        grantPoints(memberId, 10_000)
        val sellerCoupon = claimCoupon(
            buyer, sellerCouponPolicy(shop.sellerToken, couponPolicyBody(targetType = "SELLER", discountType = "FIXED", discountValue = 5_000)),
        )
        val cartCoupon = claimCoupon(buyer, adminCouponPolicy(couponPolicyBody(couponType = "CART", targetType = "NONE", discountType = "FIXED", discountValue = 4_000)))
        val quote = json(
            post(
                "/api/v1/orders/quote", buyer,
                mapOf(
                    "items" to listOf(mapOf("skuId" to d1.skuId, "quantity" to 1)),
                    "productCoupons" to listOf(mapOf("skuId" to d1.skuId, "couponId" to sellerCoupon)),
                    "cartCouponId" to cartCoupon,
                    "pointsToUse" to 3_000,
                ),
            ).andExpect { status { isOk() } },
        ).get("data")
        val total = quote.get("totalAmount").asLong()
        val order = deliveredOrder(buyer, shop.sellerToken, listOf(d1.skuId to 1), total, listOf(d1.skuId to sellerCoupon), cartCoupon, 3_000)
        val orderId = order.get("orderId").asLong()
        val itemId = items(order)[0].get("orderItemId").asLong()
        assertEquals(38_000, items(order)[0].get("paidAmount").asLong())
        assertEquals(7_000, pointBalance(memberId))

        val result = completedReturn(buyer, shop.sellerToken, orderId, itemId, "DEFECTIVE")

        assertEquals(38_000, result.get("refundAmount").asLong() - result.get("shippingFeeRefund").asLong())
        assertEquals(3_000, result.get("pointsRefund").asLong())
        assertEquals(10_000, pointBalance(memberId))
        val statuses = myCouponStatuses(buyer)
        assertEquals("AVAILABLE", statuses[sellerCoupon])
        assertEquals("AVAILABLE", statuses[cartCoupon])
        val end = orderDetail(buyer, orderId)
        assertEquals(3_000, end.get("pointsRefunded").asLong())
        assertMoneyInvariant(end, "예시 D")
    }

    @Test
    fun 변심_반품_완료_뒤_SKU_재고_plus수량_판매량_minus수량이고_불량은_재고_그대로다() {
        val shop = shop()
        val product = shop.product(30_000)
        val buyer = newUserToken()

        val order1 = deliveredOrder(buyer, shop.sellerToken, listOf(product.skuId to 2), 63_000 - 3_000)
        val stock1 = stockOf(product.skuId)
        val sales1 = salesCountOf(product.productId)
        completedReturn(buyer, shop.sellerToken, order1.get("orderId").asLong(), items(order1)[0].get("orderItemId").asLong(), "CHANGE_OF_MIND")
        assertEquals(stock1 + 2, stockOf(product.skuId))
        assertEquals(sales1 - 2, salesCountOf(product.productId))

        val order2 = deliveredOrder(buyer, shop.sellerToken, listOf(product.skuId to 1), 33_000)
        val stock2 = stockOf(product.skuId)
        val sales2 = salesCountOf(product.productId)
        completedReturn(buyer, shop.sellerToken, order2.get("orderId").asLong(), items(order2)[0].get("orderItemId").asLong(), "DEFECTIVE")
        assertEquals(stock2, stockOf(product.skuId))
        assertTrue(salesCountOf(product.productId) == sales2 - 1)
    }
}
