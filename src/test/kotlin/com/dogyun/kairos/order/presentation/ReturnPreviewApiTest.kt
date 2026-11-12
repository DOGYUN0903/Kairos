package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.CommerceTestSupport
import org.springframework.test.web.servlet.ResultActionsDsl
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReturnPreviewApiTest : CommerceTestSupport() {

    private class Shop(val sellerToken: String, val brandId: Long, val categoryId: Long, val fileIds: List<Long>)

    private fun shop(): Shop {
        val token = newUserToken()
        approvedSeller(token)
        return Shop(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)))
    }

    private fun Shop.sku(price: Long): Long {
        val productId = onSaleProduct(sellerToken, brandId, categoryId, fileIds, originalPrice = price, salePrice = price, stock = 10)
        return skuIdsOf(sellerToken, productId).sorted()[0]
    }

    private fun itemIdOfSku(order: JsonNode, skuId: Long): Long =
        order.get("sellerOrders").flatMap { it.get("items").toList() }.first { it.get("skuId").asLong() == skuId }.get("orderItemId").asLong()

    private fun firstItemId(order: JsonNode): Long = order.get("sellerOrders").get(0).get("items").get(0).get("orderItemId").asLong()

    private fun preview(token: String, orderId: Long, itemId: Long, reason: String? = "CHANGE_OF_MIND"): ResultActionsDsl =
        get("/api/v1/orders/$orderId/items/$itemId/return-preview" + if (reason == null) "" else "?reason=$reason", token)

    private fun previewData(token: String, orderId: Long, itemId: Long, reason: String): JsonNode =
        json(preview(token, orderId, itemId, reason).andExpect { status { isOk() } }).get("data")

    private fun post(token: String, orderId: Long, itemId: Long, reason: String = "CHANGE_OF_MIND"): ResultActionsDsl =
        postIdem("/api/v1/orders/$orderId/items/$itemId/returns", token, mapOf("reason" to reason))

    private fun ResultActionsDsl.expectCode(status: Int, code: String) {
        andExpect {
            status { isEqualTo(status) }
            jsonPath("$.code") { value(code) }
        }
    }

    /** 미리보기와 반품 요청이 같은 HTTP 상태·코드로 실패한다. */
    private fun assertSameError(token: String, orderId: Long, itemId: Long, status: Int, code: String, reason: String = "CHANGE_OF_MIND") {
        preview(token, orderId, itemId, reason).expectCode(status, code)
        post(token, orderId, itemId, reason).expectCode(status, code)
    }

    @Test
    fun 변심_미리보기는_14000원이고_이어서_요청_승인_수거_확인한_실제_환불과_같다() {
        val shop = shop()
        val big = shop.sku(40_000)
        val small = shop.sku(20_000)
        val buyer = newUserToken()
        val order = deliveredOrder(buyer, shop.sellerToken, listOf(big to 1, small to 1), 60_000)
        val orderId = order.get("orderId").asLong()
        val itemId = itemIdOfSku(order, small)

        val p = previewData(buyer, orderId, itemId, "CHANGE_OF_MIND")

        assertEquals(itemId, p.get("orderItemId").asLong())
        assertEquals("CHANGE_OF_MIND", p.get("reason").asString())
        assertEquals("BUYER", p.get("feeBearer").asString())
        assertEquals(20_000, p.get("paidAmount").asLong())
        assertEquals(3_000, p.get("returnFee").asLong())
        assertEquals(3_000, p.get("outboundFeeRecovered").asLong())
        assertEquals(0, p.get("shippingFeeRefund").asLong())
        assertEquals(14_000, p.get("refundAmount").asLong())
        assertEquals(0, p.get("pointsRefund").asLong())
        assertFalse(p.get("productCouponReturnable").asBoolean())

        val completed = completedReturn(buyer, shop.sellerToken, orderId, itemId, "CHANGE_OF_MIND")
        assertEquals(14_000, completed.get("refundAmount").asLong())
    }

    @Test
    fun 불량_미리보기는_33000원이다() {
        val shop = shop()
        val sku = shop.sku(30_000)
        val buyer = newUserToken()
        val order = deliveredOrder(buyer, shop.sellerToken, listOf(sku to 1), 33_000)
        val orderId = order.get("orderId").asLong()
        val itemId = firstItemId(order)

        val p = previewData(buyer, orderId, itemId, "DEFECTIVE")

        assertEquals("SELLER", p.get("feeBearer").asString())
        assertEquals(30_000, p.get("paidAmount").asLong())
        assertEquals(0, p.get("returnFee").asLong())
        assertEquals(0, p.get("outboundFeeRecovered").asLong())
        assertEquals(3_000, p.get("shippingFeeRefund").asLong())
        assertEquals(33_000, p.get("refundAmount").asLong())

        assertEquals(33_000, completedReturn(buyer, shop.sellerToken, orderId, itemId, "DEFECTIVE").get("refundAmount").asLong())
    }

    @Test
    fun 상품_쿠폰과_포인트를_쓴_항목의_미리보기는_실제_환불과_같다() {
        val shop = shop()
        val sku = shop.sku(30_000)
        val buyer = newUserToken()
        val coupon = claimCoupon(buyer, adminCouponPolicy(couponPolicyBody(discountValue = 2_000)))
        grantPoints(memberIdOf(buyer), 5_000)
        // 상품 30,000 − 쿠폰 2,000 − 포인트 1,000 + 구매자 배송비 3,000 = 30,000
        val order = deliveredOrder(buyer, shop.sellerToken, listOf(sku to 1), 30_000, productCoupons = listOf(sku to coupon), pointsToUse = 1_000)
        val orderId = order.get("orderId").asLong()
        val itemId = firstItemId(order)

        val p = previewData(buyer, orderId, itemId, "DEFECTIVE")

        assertTrue(p.get("productCouponReturnable").asBoolean())
        assertEquals(1_000, p.get("pointsRefund").asLong())
        assertEquals(27_000, p.get("paidAmount").asLong())
        assertEquals(3_000, p.get("shippingFeeRefund").asLong())

        val completed = completedReturn(buyer, shop.sellerToken, orderId, itemId, "DEFECTIVE")
        assertEquals(p.get("refundAmount").asLong(), completed.get("refundAmount").asLong())
        assertEquals(p.get("pointsRefund").asLong(), completed.get("pointsRefund").asLong())
        assertEquals(p.get("paidAmount").asLong(), completed.get("paidAmount").asLong())
    }

    @Test
    fun 미리보기는_주문과_반품을_바꾸지_않는다() {
        val shop = shop()
        val sku = shop.sku(30_000)
        val buyer = newUserToken()
        val order = deliveredOrder(buyer, shop.sellerToken, listOf(sku to 1), 33_000)
        val orderId = order.get("orderId").asLong()
        val before = orderDetail(buyer, orderId)

        previewData(buyer, orderId, firstItemId(order), "DEFECTIVE")
        previewData(buyer, orderId, firstItemId(order), "CHANGE_OF_MIND")

        assertEquals(before, orderDetail(buyer, orderId))
        val mine = json(get("/api/v1/returns/me", buyer).andExpect { status { isOk() } }).get("data")
        assertEquals(0, mine.get("totalElements").asInt())
        assertEquals(0, mine.get("content").size())
    }

    @Test
    fun 미리보기의_오류는_요청의_오류와_같다() {
        val shop = shop()
        val sku = shop.sku(30_000)

        // 배송 중인 판매자 주문
        val shippingBuyer = newUserToken()
        val shipping = paidOrder(shippingBuyer, listOf(sku to 1), 33_000)
        val soId = shipping.get("sellerOrders").get(0).get("sellerOrderId").asLong()
        patch("/api/v1/seller/orders/$soId/ship", shop.sellerToken, mapOf("carrierCode" to "04", "trackingNumber" to "T-$soId-D"))
            .andExpect { status { isOk() } }
        assertSameError(shippingBuyer, shipping.get("orderId").asLong(), firstItemId(shipping), 409, "RETURN_NOT_ELIGIBLE")

        // 구매 확정된 판매자 주문
        val confirmedBuyer = newUserToken()
        val confirmed = confirmedOrder(confirmedBuyer, shop.sellerToken, listOf(sku to 1), 33_000)
        assertSameError(confirmedBuyer, confirmed.get("orderId").asLong(), firstItemId(confirmed), 409, "RETURN_NOT_ELIGIBLE")

        // 이미 요청한 항목
        val requestedBuyer = newUserToken()
        val requested = deliveredOrder(requestedBuyer, shop.sellerToken, listOf(sku to 1), 33_000)
        requestReturn(requestedBuyer, requested.get("orderId").asLong(), firstItemId(requested))
        assertSameError(requestedBuyer, requested.get("orderId").asLong(), firstItemId(requested), 409, "RETURN_ALREADY_REQUESTED")

        // 거절된 항목
        val rejectedBuyer = newUserToken()
        val rejected = deliveredOrder(rejectedBuyer, shop.sellerToken, listOf(sku to 1), 33_000)
        rejectReturn(shop.sellerToken, requestReturn(rejectedBuyer, rejected.get("orderId").asLong(), firstItemId(rejected)))
        assertSameError(rejectedBuyer, rejected.get("orderId").asLong(), firstItemId(rejected), 409, "RETURN_ALREADY_REQUESTED")

        // 기한이 지난 항목
        val lateBuyer = newUserToken()
        val late = deliveredOrder(lateBuyer, shop.sellerToken, listOf(sku to 1), 33_000)
        jdbcTemplate.update(
            "update seller_order set delivered_at = ? where id = ?", LocalDateTime.now().minusDays(8),
            sellerOrderIdOf(late, firstItemId(late)),
        )
        assertSameError(lateBuyer, late.get("orderId").asLong(), firstItemId(late), 409, "RETURN_WINDOW_EXPIRED")

        // 남의 주문, 없는 항목
        val ownerBuyer = newUserToken()
        val owned = deliveredOrder(ownerBuyer, shop.sellerToken, listOf(sku to 1), 33_000)
        assertSameError(newUserToken(), owned.get("orderId").asLong(), firstItemId(owned), 404, "ORDER_NOT_FOUND")
        assertSameError(ownerBuyer, owned.get("orderId").asLong(), 999_999_999L, 404, "ORDER_ITEM_NOT_FOUND")

        // 모르는 사유(요청 API와 같은 400)
        assertSameError(ownerBuyer, owned.get("orderId").asLong(), firstItemId(owned), 400, "RETURN_INVALID", reason = "BOGUS")
    }

    @Test
    fun 모르는_사유는_주문_조회보다_먼저_RETURN_INVALID이고_사유가_없으면_INVALID_REQUEST다() {
        val buyer = newUserToken()

        preview(buyer, 999_999_999L, 1L, "BOGUS").expectCode(400, "RETURN_INVALID")
        preview(buyer, 999_999_999L, 1L, reason = null).expectCode(400, "INVALID_REQUEST")
        preview(buyer, 999_999_999L, 1L, reason = "").expectCode(400, "INVALID_REQUEST")
        preview(buyer, 999_999_999L, 1L, "CHANGE_OF_MIND").expectCode(404, "ORDER_NOT_FOUND")
    }

    @Test
    fun 로그인하지_않으면_401이다() {
        get("/api/v1/orders/1/items/1/return-preview?reason=CHANGE_OF_MIND").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun 철회한_항목은_미리보기가_다시_된다() {
        val shop = shop()
        val sku = shop.sku(30_000)
        val buyer = newUserToken()
        val order = deliveredOrder(buyer, shop.sellerToken, listOf(sku to 1), 33_000)
        val orderId = order.get("orderId").asLong()
        val itemId = firstItemId(order)
        val returnId = requestReturn(buyer, orderId, itemId)
        preview(buyer, orderId, itemId).expectCode(409, "RETURN_ALREADY_REQUESTED")

        delete("/api/v1/returns/$returnId", buyer).andExpect { status { isOk() } }

        assertEquals(27_000, previewData(buyer, orderId, itemId, "CHANGE_OF_MIND").get("refundAmount").asLong())
    }
}
