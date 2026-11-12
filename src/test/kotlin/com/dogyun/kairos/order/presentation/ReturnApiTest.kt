package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.CommerceTestSupport
import org.springframework.test.web.servlet.ResultActionsDsl
import java.time.LocalDateTime
import java.util.UUID
import tools.jackson.databind.JsonNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReturnApiTest : CommerceTestSupport() {

    private class Shop(val sellerToken: String, val productId: Long, val skuId: Long)

    private fun shop(salePrice: Long = 30_000): Shop {
        val token = newUserToken()
        approvedSeller(token)
        val productId = onSaleProduct(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)), originalPrice = salePrice, salePrice = salePrice)
        return Shop(token, productId, skuIdsOf(token, productId).sorted()[0])
    }

    private fun itemIdOf(order: JsonNode): Long = order.get("sellerOrders").get(0).get("items").get(0).get("orderItemId").asLong()

    private fun returnPost(token: String, orderId: Long, itemId: Long, key: String = UUID.randomUUID().toString(), reason: String = "CHANGE_OF_MIND"): ResultActionsDsl =
        postIdem("/api/v1/orders/$orderId/items/$itemId/returns", token, mapOf("reason" to reason), key)

    private fun ResultActionsDsl.expectCode(status: Int, code: String) {
        andExpect {
            status { isEqualTo(status) }
            jsonPath("$.code") { value(code) }
        }
    }

    @Test
    fun 배송_완료_항목에_반품을_요청하면_201이고_주문_상세_항목에_returnRequest와_returnableUntil이_보인다() {
        val shop = shop()
        val buyer = newUserToken()
        val order = deliveredOrder(buyer, shop.sellerToken, listOf(shop.skuId to 1), 33_000)
        val orderId = order.get("orderId").asLong()
        val itemId = itemIdOf(order)

        val returnId = requestReturn(buyer, orderId, itemId, reason = "DEFECTIVE", detail = "찢어져 왔어요")

        val detail = orderDetail(buyer, orderId)
        val so = detail.get("sellerOrders").get(0)
        val item = so.get("items").get(0)
        assertEquals("RETURN_REQUESTED", item.get("status").asString())
        assertEquals(returnId, item.get("returnRequest").get("returnId").asLong())
        assertEquals("REQUESTED", item.get("returnRequest").get("status").asString())
        assertEquals("SELLER", item.get("returnRequest").get("feeBearer").asString())
        val deliveredAt = LocalDateTime.parse(so.get("deliveredAt").asString())
        assertEquals(deliveredAt.plusDays(7), LocalDateTime.parse(so.get("returnableUntil").asString()))
        assertTrue(so.get("hasOpenReturn").asBoolean())
        assertEquals(false, so.get("allItemsReturned").asBoolean())
        assertEquals(30_000, detail.get("remainingPaidAmount").asLong())
        assertEquals(3_000, detail.get("remainingBuyerShippingFee").asLong())

        val mine = returnDetail(buyer, returnId)
        assertEquals("DEFECTIVE", mine.get("reason").asString())
        assertEquals(1, mine.get("quantity").asInt())
        val list = json(get("/api/v1/returns/me", buyer).andExpect { status { isOk() } }).get("data")
        assertEquals(1, list.get("totalElements").asInt())
        assertEquals(returnId, list.get("content").get(0).get("returnId").asLong())
    }

    @Test
    fun 반품_응답에_상품_이미지_옵션_주문번호_판매자_이름_결제_금액이_있다() {
        val shop = shop()
        val buyer = newUserToken()
        val order = deliveredOrder(buyer, shop.sellerToken, listOf(shop.skuId to 1), 33_000)
        val orderId = order.get("orderId").asLong()
        val orderItem = order.get("sellerOrders").get(0).get("items").get(0)
        val itemId = orderItem.get("orderItemId").asLong()
        val thumbnail = orderItem.get("thumbnailUrl").asString()
        assertTrue(thumbnail.isNotBlank())

        val requestResponse = json(returnPost(buyer, orderId, itemId).andExpect { status { isCreated() } }).get("data")
        val returnId = requestResponse.get("returnId").asLong()

        fun check(r: JsonNode) {
            assertEquals(thumbnail, r.get("thumbnailUrl").asString())
            assertEquals(orderItem.get("optionDisplayName").asString(), r.get("optionDisplayName").asString())
            assertEquals(order.get("orderNo").asString(), r.get("orderNo").asString())
            assertEquals(order.get("sellerOrders").get(0).get("sellerName").asString(), r.get("sellerName").asString())
            assertEquals(orderItem.get("paidAmount").asLong(), r.get("paidAmount").asLong())
            assertEquals(30_000, r.get("paidAmount").asLong())
        }
        check(requestResponse)
        check(returnDetail(buyer, returnId))
        check(json(get("/api/v1/returns/me", buyer).andExpect { status { isOk() } }).get("data").get("content").get(0))
        check(json(get("/api/v1/seller/returns/$returnId", shop.sellerToken).andExpect { status { isOk() } }).get("data"))
        check(json(get("/api/v1/seller/returns", shop.sellerToken).andExpect { status { isOk() } }).get("data").get("content").get(0))
        check(json(get("/api/v1/admin/returns/$returnId", adminToken()).andExpect { status { isOk() } }).get("data"))
        val adminList = json(get("/api/v1/admin/returns?status=REQUESTED", adminToken()).andExpect { status { isOk() } }).get("data").get("content")
        check(adminList.first { it.get("returnId").asLong() == returnId })
    }

    @Test
    fun 자동_승인_예정_시각은_요청_상태에서만_있고_요청_시각에_3일을_더한_값이다() {
        val shop = shop()
        val buyer = newUserToken()
        val order = deliveredOrder(buyer, shop.sellerToken, listOf(shop.skuId to 1), 33_000)
        val returnId = requestReturn(buyer, order.get("orderId").asLong(), itemIdOf(order))

        val requested = returnDetail(buyer, returnId)
        assertEquals(LocalDateTime.parse(requested.get("requestedAt").asString()).plusDays(3), LocalDateTime.parse(requested.get("autoApproveAt").asString()))

        approveReturn(shop.sellerToken, returnId)
        assertTrue(returnDetail(buyer, returnId).get("autoApproveAt").isNull)
    }

    @Test
    fun 같은_멱등_키_재요청은_같은_returnId이고_다른_키는_409_RETURN_ALREADY_REQUESTED() {
        val shop = shop()
        val buyer = newUserToken()
        val order = deliveredOrder(buyer, shop.sellerToken, listOf(shop.skuId to 1), 33_000)
        val orderId = order.get("orderId").asLong()
        val itemId = itemIdOf(order)
        val key = UUID.randomUUID().toString()

        val first = json(returnPost(buyer, orderId, itemId, key).andExpect { status { isCreated() } }).get("data").get("returnId").asLong()
        val replay = json(returnPost(buyer, orderId, itemId, key).andExpect { status { isCreated() } }).get("data").get("returnId").asLong()

        assertEquals(first, replay)
        returnPost(buyer, orderId, itemId).expectCode(409, "RETURN_ALREADY_REQUESTED")
    }

    @Test
    fun delivered_at을_8일_전으로_바꾸면_409_RETURN_WINDOW_EXPIRED() {
        val shop = shop()
        val buyer = newUserToken()
        val order = deliveredOrder(buyer, shop.sellerToken, listOf(shop.skuId to 1), 33_000)
        val orderId = order.get("orderId").asLong()
        val itemId = itemIdOf(order)
        jdbcTemplate.update(
            "update seller_order set delivered_at = ? where id = ?", LocalDateTime.now().minusDays(8), sellerOrderIdOf(order, itemId),
        )

        returnPost(buyer, orderId, itemId).expectCode(409, "RETURN_WINDOW_EXPIRED")
    }

    @Test
    fun 구매_확정된_판매자_주문은_409_RETURN_NOT_ELIGIBLE() {
        val shop = shop()
        val buyer = newUserToken()
        val order = confirmedOrder(buyer, shop.sellerToken, listOf(shop.skuId to 1), 33_000)

        returnPost(buyer, order.get("orderId").asLong(), itemIdOf(order)).expectCode(409, "RETURN_NOT_ELIGIBLE")
    }

    @Test
    fun 철회하면_항목이_ORDERED이고_다시_요청하면_같은_returnId다() {
        val shop = shop()
        val buyer = newUserToken()
        val order = deliveredOrder(buyer, shop.sellerToken, listOf(shop.skuId to 1), 33_000)
        val orderId = order.get("orderId").asLong()
        val itemId = itemIdOf(order)
        val returnId = requestReturn(buyer, orderId, itemId)

        val withdrawn = json(delete("/api/v1/returns/$returnId", buyer).andExpect { status { isOk() } }).get("data")
        assertEquals("WITHDRAWN", withdrawn.get("status").asString())
        delete("/api/v1/returns/$returnId", buyer).andExpect { status { isOk() } }
        assertEquals("ORDERED", orderDetail(buyer, orderId).get("sellerOrders").get(0).get("items").get(0).get("status").asString())

        assertEquals(returnId, requestReturn(buyer, orderId, itemId, reason = "WRONG_DELIVERY"))
        assertEquals("WRONG_DELIVERY", returnDetail(buyer, returnId).get("reason").asString())
    }

    @Test
    fun 배송_중_항목은_409_RETURN_NOT_ELIGIBLE() {
        val shop = shop()
        val buyer = newUserToken()
        val order = paidOrder(buyer, listOf(shop.skuId to 1), 33_000)
        val itemId = itemIdOf(order)
        val soId = sellerOrderIdOf(order, itemId)
        patch("/api/v1/seller/orders/$soId/ship", shop.sellerToken, mapOf("carrierCode" to "04", "trackingNumber" to "T-$soId-D"))
            .andExpect { status { isOk() } }

        returnPost(buyer, order.get("orderId").asLong(), itemId).expectCode(409, "RETURN_NOT_ELIGIBLE")
    }

    @Test
    fun 남의_반품_조회는_404() {
        val shop = shop()
        val buyer = newUserToken()
        val order = deliveredOrder(buyer, shop.sellerToken, listOf(shop.skuId to 1), 33_000)
        val returnId = requestReturn(buyer, order.get("orderId").asLong(), itemIdOf(order))
        val other = newUserToken()

        get("/api/v1/returns/$returnId", other).andReturn().let { assertEquals(404, it.response.status) }
        get("/api/v1/returns/$returnId", other).expectCode(404, "RETURN_NOT_FOUND")
        delete("/api/v1/returns/$returnId", other).expectCode(404, "RETURN_NOT_FOUND")
        returnPost(other, order.get("orderId").asLong(), itemIdOf(order)).expectCode(404, "ORDER_NOT_FOUND")
    }
}
