package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.CommerceTestSupport
import org.springframework.test.web.servlet.ResultActionsDsl
import tools.jackson.databind.JsonNode
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminReturnApiTest : CommerceTestSupport() {

    private class Requested(val seller: String, val buyer: String, val orderId: Long, val sellerOrderId: Long, val itemId: Long, val returnId: Long)

    private fun requested(price: Long = 30_000, reason: String = "CHANGE_OF_MIND"): Requested {
        val seller = newUserToken()
        approvedSeller(seller)
        val productId = onSaleProduct(seller, approvedBrand(seller), leafCategory(), listOf(uploadImage(seller)), originalPrice = price, salePrice = price)
        val skuId = skuIdsOf(seller, productId).sorted()[0]
        val buyer = newUserToken()
        val order = deliveredOrder(buyer, seller, listOf(skuId to 1), price + 3_000)
        val orderId = order.get("orderId").asLong()
        val so = order.get("sellerOrders").get(0)
        val itemId = so.get("items").get(0).get("orderItemId").asLong()
        return Requested(seller, buyer, orderId, so.get("sellerOrderId").asLong(), itemId, requestReturn(buyer, orderId, itemId, reason))
    }

    private fun admin(path: String): JsonNode = json(patch(path, adminToken()).andExpect { status { isOk() } }).get("data")

    private fun itemStatus(order: JsonNode): String = order.get("sellerOrders").get(0).get("items").get(0).get("status").asString()

    private fun ResultActionsDsl.expectCode(status: Int, code: String) {
        andExpect {
            status { isEqualTo(status) }
            jsonPath("$.code") { value(code) }
        }
    }

    @Test
    fun 관리자는_거절된_반품을_승인할_수_있다() {
        val r = requested()
        rejectReturn(r.seller, r.returnId)

        val approved = admin("/api/v1/admin/returns/${r.returnId}/approve")

        assertEquals("APPROVED", approved.get("status").asString())
        assertEquals("ADMIN", approved.get("decidedBy").asString())
        assertEquals("RETURN_APPROVED", itemStatus(orderDetail(r.buyer, r.orderId)))
        assertEquals("APPROVED", admin("/api/v1/admin/returns/${r.returnId}/approve").get("status").asString())
        val read = json(get("/api/v1/admin/returns/${r.returnId}", adminToken()).andExpect { status { isOk() } }).get("data")
        assertEquals("APPROVED", read.get("status").asString())
        val list = json(get("/api/v1/admin/returns?status=APPROVED&sellerId=0", adminToken()).andExpect { status { isOk() } }).get("data")
        assertEquals(0, list.get("totalElements").asInt())
        val all = json(get("/api/v1/admin/returns?status=APPROVED&memberId=${memberIdOf(r.buyer)}", adminToken()).andExpect { status { isOk() } }).get("data")
        assertEquals(1, all.get("totalElements").asInt())
    }

    @Test
    fun 확정된_판매자_주문의_거절_반품_승인은_409_RETURN_NOT_ELIGIBLE() {
        val r = requested()
        rejectReturn(r.seller, r.returnId)
        patch("/api/v1/orders/${r.orderId}/seller-orders/${r.sellerOrderId}/confirm", r.buyer).andExpect { status { isOk() } }

        patch("/api/v1/admin/returns/${r.returnId}/approve", adminToken()).expectCode(409, "RETURN_NOT_ELIGIBLE")

        assertEquals("ORDERED", itemStatus(orderDetail(r.buyer, r.orderId)))
    }

    @Test
    fun 관리자_수거_확인은_판매자와_같은_환불이다() {
        val bySeller = requested()
        approveReturn(bySeller.seller, bySeller.returnId)
        val sellerResult = receiveReturn(bySeller.seller, bySeller.returnId)

        val byAdmin = requested()
        approveReturn(byAdmin.seller, byAdmin.returnId)
        val adminResult = admin("/api/v1/admin/returns/${byAdmin.returnId}/receive")

        assertEquals("COMPLETED", adminResult.get("status").asString())
        assertEquals(sellerResult.get("refundAmount").asLong(), adminResult.get("refundAmount").asLong())
        assertEquals(sellerResult.get("returnFee").asLong(), adminResult.get("returnFee").asLong())
        assertEquals(sellerResult.get("outboundFeeRecovered").asLong(), adminResult.get("outboundFeeRecovered").asLong())
        assertEquals("COMPLETED", admin("/api/v1/admin/returns/${byAdmin.returnId}/receive").get("status").asString())
    }

    @Test
    fun 승인_상태_관리자_취소는_항목_ORDERED_반품_CANCELED_BY_ADMIN이다() {
        val r = requested()
        approveReturn(r.seller, r.returnId)

        val canceled = admin("/api/v1/admin/returns/${r.returnId}/cancel")

        assertEquals("CANCELED_BY_ADMIN", canceled.get("status").asString())
        assertEquals("ORDERED", itemStatus(orderDetail(r.buyer, r.orderId)))
        assertEquals("CANCELED_BY_ADMIN", admin("/api/v1/admin/returns/${r.returnId}/cancel").get("status").asString())
        patch("/api/v1/admin/returns/${r.returnId}/receive", adminToken()).expectCode(409, "RETURN_INVALID_STATE_TRANSITION")
    }

    @Test
    fun 일반_회원_판매자_토큰은_403() {
        val r = requested()
        val member = newUserToken()

        get("/api/v1/admin/returns", member).andExpect { status { isForbidden() } }
        get("/api/v1/admin/returns", r.seller).andExpect { status { isForbidden() } }
        patch("/api/v1/admin/returns/${r.returnId}/approve", r.seller).andExpect { status { isForbidden() } }
        patch("/api/v1/admin/returns/${r.returnId}/receive", member).andExpect { status { isForbidden() } }
        patch("/api/v1/admin/returns/${r.returnId}/cancel", member).andExpect { status { isForbidden() } }
        get("/api/v1/admin/returns/${r.returnId}", member).andExpect { status { isForbidden() } }
    }
}
