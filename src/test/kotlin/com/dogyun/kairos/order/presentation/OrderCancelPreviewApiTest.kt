package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.CommerceTestSupport
import org.springframework.test.web.servlet.ResultActionsDsl
import tools.jackson.databind.JsonNode
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderCancelPreviewApiTest : CommerceTestSupport() {

    private class Shop(val sellerToken: String, val productId: Long, val skuId: Long)

    private fun shop(salePrice: Long): Shop {
        val token = newUserToken()
        approvedSeller(token)
        val productId = onSaleProduct(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)), originalPrice = salePrice, salePrice = salePrice, stock = 5)
        return Shop(token, productId, skuIdsOf(token, productId).sorted()[0])
    }

    /** 판매자 주문은 판매자 ID 순이라 상점을 만든 순서와 같다. */
    private fun itemId(order: JsonNode, index: Int): Long =
        order.get("sellerOrders").get(index).get("items").get(0).get("orderItemId").asLong()

    private fun pay(user: String, items: List<Pair<Shop, Int>>, total: Long): JsonNode =
        paidOrder(user, items.map { it.first.skuId to it.second }, total)

    private fun preview(user: String, orderId: Long, itemId: Long): ResultActionsDsl =
        get("/api/v1/orders/$orderId/items/$itemId/cancel-preview", user)

    private fun previewData(user: String, orderId: Long, itemId: Long): JsonNode =
        json(preview(user, orderId, itemId).andExpect { status { isOk() } }).get("data")

    private fun ResultActionsDsl.expectCode(status: Int, code: String) {
        andExpect {
            status { isEqualTo(status) }
            jsonPath("$.code") { value(code) }
        }
    }

    private fun assertPreview(p: JsonNode, paid: Long, refund: Long, deducted: Long, returned: Long, refundAmount: Long) {
        assertEquals(paid, p.get("paidAmount").asLong())
        assertEquals(refund, p.get("shippingFeeRefund").asLong())
        assertEquals(deducted, p.get("shippingFeeDeducted").asLong())
        assertEquals(returned, p.get("shippingFeeReturned").asLong())
        assertEquals(refundAmount, p.get("refundAmount").asLong())
    }

    @Test
    fun 무료배송_주문에서_한_판매자_항목을_취소하면_배송비_3000원이_차감된_27000원이_미리보인다() {
        val b = shop(30_000)
        val c = shop(30_000)
        val user = newUserToken()
        val order = pay(user, listOf(b to 1, c to 1), 60_000)
        val p = previewData(user, order.get("orderId").asLong(), itemId(order, 1))
        assertEquals(itemId(order, 1), p.get("orderItemId").asLong())
        assertPreview(p, paid = 30_000, refund = 0, deducted = 3_000, returned = 0, refundAmount = 27_000)
    }

    @Test
    fun 남은_금액이_정확히_5만원이면_차감하지_않는다() {
        val b = shop(50_000)
        val c = shop(10_000)
        val user = newUserToken()
        val order = pay(user, listOf(b to 1, c to 1), 60_000)
        val p = previewData(user, order.get("orderId").asLong(), itemId(order, 1))
        assertPreview(p, paid = 10_000, refund = 0, deducted = 0, returned = 0, refundAmount = 10_000)
    }

    @Test
    fun 이미_차감된_뒤의_취소는_다시_차감하지_않는다() {
        val a = shop(20_000)
        val b = shop(20_000)
        val c = shop(20_000)
        val user = newUserToken()
        val order = pay(user, listOf(a to 1, b to 1, c to 1), 60_000)
        val orderId = order.get("orderId").asLong()
        val first = cancelItem(user, orderId, itemId(order, 0))
        assertEquals(17_000L, first.get("refundAmount").asLong())
        assertEquals(3_000L, first.get("shippingFeeDeducted").asLong())

        val p = previewData(user, orderId, itemId(order, 1))
        assertPreview(p, paid = 20_000, refund = 0, deducted = 0, returned = 0, refundAmount = 20_000)
    }

    @Test
    fun 마지막_항목_취소는_앞서_차감한_배송비를_돌려준다() {
        val b = shop(30_000)
        val c = shop(30_000)
        val user = newUserToken()
        val order = pay(user, listOf(b to 1, c to 1), 60_000)
        val orderId = order.get("orderId").asLong()
        cancelItem(user, orderId, itemId(order, 0))

        val p = previewData(user, orderId, itemId(order, 1))
        assertPreview(p, paid = 30_000, refund = 0, deducted = 0, returned = 3_000, refundAmount = 33_000)
    }

    @Test
    fun 구매자가_배송비를_낸_판매자_주문이_비면_배송비도_환불된다() {
        val b = shop(20_000)
        val c = shop(20_000)
        val user = newUserToken()
        val order = pay(user, listOf(b to 1, c to 1), 46_000)
        val p = previewData(user, order.get("orderId").asLong(), itemId(order, 1))
        assertPreview(p, paid = 20_000, refund = 3_000, deducted = 0, returned = 0, refundAmount = 23_000)
    }

    @Test
    fun 미리보기_금액은_직후_실제_취소_응답과_같다() {
        val b = shop(30_000)
        val c = shop(30_000)
        val user = newUserToken()
        val order = pay(user, listOf(b to 1, c to 1), 60_000)
        val orderId = order.get("orderId").asLong()
        val itemId = itemId(order, 1)

        val p = previewData(user, orderId, itemId)
        val canceled = cancelItem(user, orderId, itemId)
        assertEquals(canceled.get("refundAmount").asLong(), p.get("refundAmount").asLong())
        assertEquals(canceled.get("shippingFeeDeducted").asLong(), p.get("shippingFeeDeducted").asLong())
        assertEquals(canceled.get("pointsRefund").asLong(), p.get("pointsRefund").asLong())
    }

    @Test
    fun 미리보기는_주문_결제_재고를_바꾸지_않는다() {
        val b = shop(30_000)
        val c = shop(30_000)
        val user = newUserToken()
        val order = pay(user, listOf(b to 1, c to 1), 60_000)
        val orderId = order.get("orderId").asLong()
        val stockBefore = stockOf(c.skuId)
        val before = orderDetail(user, orderId).toString()

        previewData(user, orderId, itemId(order, 1))

        assertEquals(before, orderDetail(user, orderId).toString())
        assertEquals(stockBefore, stockOf(c.skuId))
    }

    @Test
    fun 남의_주문은_404다() {
        val b = shop(30_000)
        val user = newUserToken()
        val order = pay(user, listOf(b to 1), 33_000)
        preview(newUserToken(), order.get("orderId").asLong(), itemId(order, 0)).expectCode(404, "ORDER_NOT_FOUND")
    }

    @Test
    fun 없는_항목은_404다() {
        val b = shop(30_000)
        val user = newUserToken()
        val order = pay(user, listOf(b to 1), 33_000)
        preview(user, order.get("orderId").asLong(), 999_999_999L).expectCode(404, "ORDER_ITEM_NOT_FOUND")
    }

    @Test
    fun 배송이_시작된_항목은_409다() {
        val b = shop(30_000)
        val user = newUserToken()
        val order = pay(user, listOf(b to 1), 33_000)
        val soId = order.get("sellerOrders").get(0).get("sellerOrderId").asLong()
        patch("/api/v1/seller/orders/$soId/ship", b.sellerToken, mapOf("carrierCode" to "04", "trackingNumber" to "T-1"))
            .andExpect { status { isOk() } }
        preview(user, order.get("orderId").asLong(), itemId(order, 0)).expectCode(409, "ORDER_ITEM_NOT_CANCELABLE")
    }

    @Test
    fun 결제_전_주문은_409다() {
        val b = shop(30_000)
        val user = newUserToken()
        val orderNo = json(placeOrder(user, listOf(b.skuId to 1), 33_000).andExpect { status { isCreated() } }).get("data").get("orderNo").asString()
        val orderId = json(get("/api/v1/orders", user).andExpect { status { isOk() } }).get("data").get("content").first {
            it.get("orderNo").asString() == orderNo
        }.get("orderId").asLong()
        val detail = orderDetail(user, orderId)
        preview(user, orderId, itemId(detail, 0)).expectCode(409, "ORDER_INVALID_STATE_TRANSITION")
    }
}
