package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.CommerceTestSupport
import tools.jackson.databind.JsonNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrderItemReviewStateApiTest : CommerceTestSupport() {

    private fun reviewOf(token: String, orderId: Long): JsonNode =
        orderDetail(token, orderId).get("sellerOrders").get(0).get("items").get(0).get("review")

    @Test
    fun 확정_전에는_NOT_ELIGIBLE_확정_후에는_WRITABLE_작성_후에는_WRITTEN_삭제_후에는_DELETED다() {
        val sellerToken = newUserToken()
        approvedSeller(sellerToken)
        val productId = onSaleProduct(sellerToken, approvedBrand(sellerToken), leafCategory(), listOf(uploadImage(sellerToken)), originalPrice = 20_000, salePrice = 20_000, stock = 5)
        val skuId = skuIdsOf(sellerToken, productId).first()
        val user = newUserToken()

        val paid = paidOrder(user, listOf(skuId to 1), 23_000)
        val orderId = paid.get("orderId").asLong()
        val soId = paid.get("sellerOrders").get(0).get("sellerOrderId").asLong()
        val itemId = paid.get("sellerOrders").get(0).get("items").get(0).get("orderItemId").asLong()
        assertEquals("NOT_ELIGIBLE", reviewOf(user, orderId).get("status").asString())

        patch("/api/v1/seller/orders/$soId/ship", sellerToken, mapOf("carrierCode" to "04", "trackingNumber" to "T-$soId-D")).andExpect { status { isOk() } }
        patch("/api/v1/seller/orders/$soId/deliver", sellerToken).andExpect { status { isOk() } }
        assertEquals("NOT_ELIGIBLE", reviewOf(user, orderId).get("status").asString())

        patch("/api/v1/orders/$orderId/seller-orders/$soId/confirm", user).andExpect { status { isOk() } }
        val writable = reviewOf(user, orderId)
        assertEquals("WRITABLE", writable.get("status").asString())
        assertTrue(writable.get("reviewId") == null || writable.get("reviewId").isNull)
        val confirmedAt = orderDetail(user, orderId).get("sellerOrders").get(0).get("confirmedAt").asString()
        assertEquals(java.time.LocalDateTime.parse(confirmedAt).plusDays(90), java.time.LocalDateTime.parse(writable.get("writableUntil").asString()))

        val reviewId = writeReview(user, orderId, itemId)
        val written = reviewOf(user, orderId)
        assertEquals("WRITTEN", written.get("status").asString())
        assertEquals(reviewId, written.get("reviewId").asLong())

        delete("/api/v1/reviews/$reviewId", user).andExpect { status { isNoContent() } }
        assertEquals("DELETED", reviewOf(user, orderId).get("status").asString())

        // 판매자 응답은 review 가 null 이다.
        val sellerItem = json(get("/api/v1/seller/orders/$soId", sellerToken).andExpect { status { isOk() } }).get("data").get("items").get(0)
        assertTrue(sellerItem.has("review") && sellerItem.get("review").isNull)
    }
}
