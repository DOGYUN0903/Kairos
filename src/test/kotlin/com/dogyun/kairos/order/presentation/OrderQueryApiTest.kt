package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.CommerceTestSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrderQueryApiTest : CommerceTestSupport() {

    private class Shop(val sellerToken: String, val skuId: Long)

    private fun shop(): Shop {
        val token = newUserToken()
        approvedSeller(token)
        val productId = onSaleProduct(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)), salePrice = 8_000, stock = 20)
        return Shop(token, skuIdsOf(token, productId).sorted()[0])
    }

    /** 8000 원 상품 1개 + 배송비 3000. */
    private fun pay(user: String, shop: Shop) = paidOrder(user, listOf(shop.skuId to 1), 11_000)

    @Test
    fun 결제한_주문이_내_목록과_상세에_보이고_판매자별_묶음과_결제_정보가_있다() {
        val shop = shop()
        val user = newUserToken()
        val detail = pay(user, shop)
        val orderId = detail.get("orderId").asLong()

        assertEquals("PAID", detail.get("status").asString())
        assertEquals(11_000L, detail.get("totalAmount").asLong())
        assertEquals("홍길동", detail.get("shippingAddress").get("receiverName").asString())
        assertEquals(1, detail.get("sellerOrders").size())
        val sellerOrder = detail.get("sellerOrders").get(0)
        assertEquals("PAID", sellerOrder.get("status").asString())
        assertEquals("키로스상사", sellerOrder.get("sellerName").asString())
        assertEquals(1, sellerOrder.get("items").size())
        assertEquals("ORDERED", sellerOrder.get("items").get(0).get("status").asString())
        assertEquals(8_000L, sellerOrder.get("items").get(0).get("lineAmount").asLong())
        assertEquals("APPROVED", detail.get("payment").get("status").asString())
        assertEquals(11_000L, detail.get("payment").get("amount").asLong())
        assertTrue(detail.get("payment").get("paymentKey").asString().startsWith("pay-"))

        val list = json(get("/api/v1/orders", user).andExpect { status { isOk() } }).get("data")
        assertEquals(1L, list.get("totalElements").asLong())
        val summary = list.get("content").get(0)
        assertEquals(orderId, summary.get("orderId").asLong())
        assertEquals(1, summary.get("itemCount").asInt())
        assertEquals("티셔츠", summary.get("firstProductName").asString())

        get("/api/v1/orders/$orderId", newUserToken()).andExpect { status { isNotFound() } }
        assertEquals(0L, json(get("/api/v1/orders", newUserToken()).andExpect { status { isOk() } }).get("data").get("totalElements").asLong())
    }

    @Test
    fun 판매자는_자기_판매자_주문만_보고_배송지가_보인다() {
        val shop = shop()
        val other = shop()
        val user = newUserToken()
        val detail = pay(user, shop)
        pay(user, other)
        val sellerOrderId = detail.get("sellerOrders").get(0).get("sellerOrderId").asLong()

        val list = json(get("/api/v1/seller/orders", shop.sellerToken).andExpect { status { isOk() } }).get("data")
        assertEquals(1L, list.get("totalElements").asLong())
        assertEquals(sellerOrderId, list.get("content").get(0).get("sellerOrderId").asLong())
        assertEquals(8_000L, list.get("content").get(0).get("itemsAmount").asLong())

        val filtered = json(get("/api/v1/seller/orders?status=SHIPPING", shop.sellerToken).andExpect { status { isOk() } }).get("data")
        assertEquals(0L, filtered.get("totalElements").asLong())

        val seen = json(get("/api/v1/seller/orders/$sellerOrderId", shop.sellerToken).andExpect { status { isOk() } }).get("data")
        assertEquals("홍길동", seen.get("shippingAddress").get("receiverName").asString())
        assertEquals("010-1234-5678", seen.get("shippingAddress").get("phone").asString())
        assertEquals(detail.get("orderNo").asString(), seen.get("orderNo").asString())
        assertEquals(1, seen.get("items").size())
    }

    @Test
    fun 다른_판매자의_판매자_주문_상세는_404() {
        val shop = shop()
        val other = shop()
        val sellerOrderId = pay(newUserToken(), shop).get("sellerOrders").get(0).get("sellerOrderId").asLong()

        get("/api/v1/seller/orders/$sellerOrderId", other.sellerToken).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SELLER_ORDER_NOT_FOUND") }
        }
    }

    @Test
    fun 결제_전_판매자_주문은_판매자_목록과_상세에_보이지_않는다() {
        val shop = shop()
        val user = newUserToken()
        placeOrder(user, listOf(shop.skuId to 1), 11_000).andExpect { status { isCreated() } }

        assertEquals(0L, json(get("/api/v1/seller/orders", shop.sellerToken).andExpect { status { isOk() } }).get("data").get("totalElements").asLong())
        val orderId = jdbcTemplate.queryForObject("select max(id) from orders", Long::class.java)!!
        val sellerOrderId = jdbcTemplate.queryForObject("select id from seller_order where order_id = ?", Long::class.java, orderId)!!
        get("/api/v1/seller/orders/$sellerOrderId", shop.sellerToken).andExpect { status { isNotFound() } }
        // 구매자에게는 결제 대기 주문이 보인다.
        assertEquals("PENDING_PAYMENT", json(get("/api/v1/orders/$orderId", user).andExpect { status { isOk() } }).get("data").get("status").asString())
    }

    @Test
    fun 관리자는_주문번호로_찾는다() {
        val shop = shop()
        val user = newUserToken()
        val detail = pay(user, shop)
        pay(user, shop)
        val orderNo = detail.get("orderNo").asString()

        val found = json(get("/api/v1/admin/orders?orderNo=$orderNo", adminToken()).andExpect { status { isOk() } }).get("data")
        assertEquals(1L, found.get("totalElements").asLong())
        assertEquals(detail.get("orderId").asLong(), found.get("content").get(0).get("orderId").asLong())

        val all = json(get("/api/v1/admin/orders?status=PAID", adminToken()).andExpect { status { isOk() } }).get("data")
        assertEquals(2L, all.get("totalElements").asLong())

        val one = json(get("/api/v1/admin/orders/${detail.get("orderId").asLong()}", adminToken()).andExpect { status { isOk() } }).get("data")
        assertEquals(orderNo, one.get("orderNo").asString())
        assertEquals("APPROVED", one.get("payment").get("status").asString())
    }

    @Test
    fun 일반_회원의_관리자_주문_조회는_403() {
        val user = newUserToken()

        get("/api/v1/admin/orders", user).andExpect { status { isForbidden() } }
        get("/api/v1/admin/orders/1", user).andExpect { status { isForbidden() } }
    }

    @Test
    fun 판매자가_아닌_회원의_판매자_주문_조회는_403() {
        get("/api/v1/seller/orders", newUserToken()).andExpect { status { isForbidden() } }
    }

    @Test
    fun 판매자_주문_상세에_항목별_productCouponDiscount와_productCouponIssuer가_보인다() {
        val sellerToken = newUserToken()
        approvedSeller(sellerToken)
        val productId = onSaleProduct(sellerToken, approvedBrand(sellerToken), leafCategory(), listOf(uploadImage(sellerToken)), originalPrice = 20_000, salePrice = 20_000, stock = 5)
        val skuId = skuIdsOf(sellerToken, productId).sorted()[0]
        val policyId = sellerCouponPolicy(
            sellerToken, couponPolicyBody(targetType = "SELLER", discountType = "RATE", discountValue = 1000, maxDiscount = 5_000),
        )
        val buyer = newUserToken()
        val couponId = claimCoupon(buyer, policyId)

        // 20,000 x 2 = 40,000, 판매자 정률 10% = 4,000, 배송비 3,000
        val order = paidOrder(buyer, listOf(skuId to 2), 39_000, productCoupons = listOf(skuId to couponId))
        val sellerOrderId = order.get("sellerOrders").get(0).get("sellerOrderId").asLong()

        val item = json(get("/api/v1/seller/orders/$sellerOrderId", sellerToken).andExpect { status { isOk() } }).get("data").get("items").get(0)
        assertEquals(4_000L, item.get("productCouponDiscount").asLong())
        assertEquals("SELLER", item.get("productCouponIssuer").asString())
        assertEquals(couponId, item.get("productCouponId").asLong())
        assertEquals(36_000L, item.get("paidAmount").asLong())
    }

    private fun imageUrlOf(productId: Long): String =
        json(get("/api/v1/products/$productId").andExpect { status { isOk() } }).get("data").get("imageUrls").get(0).asString()

    @Test
    fun 주문_목록과_상세에_상품_대표_이미지가_있다() {
        val sellerToken = newUserToken()
        approvedSeller(sellerToken)
        val productId = onSaleProduct(sellerToken, approvedBrand(sellerToken), leafCategory(), listOf(uploadImage(sellerToken)), salePrice = 8_000, stock = 20)
        val skuId = skuIdsOf(sellerToken, productId).sorted()[0]
        val expected = imageUrlOf(productId)
        val user = newUserToken()
        val detail = paidOrder(user, listOf(skuId to 1), 11_000)

        assertEquals(expected, detail.get("sellerOrders").get(0).get("items").get(0).get("thumbnailUrl").asString())
        val list = json(get("/api/v1/orders", user).andExpect { status { isOk() } }).get("data")
        assertEquals(expected, list.get("content").get(0).get("thumbnailUrl").asString())
        val sellerList = json(get("/api/v1/seller/orders", sellerToken).andExpect { status { isOk() } }).get("data")
        assertEquals(expected, sellerList.get("content").get(0).get("thumbnailUrl").asString())
    }

    @Test
    fun 주문_목록에_판매자_주문_상태가_있다() {
        val a = shop()
        val b = shop()
        val user = newUserToken()
        val detail = paidOrder(user, listOf(a.skuId to 1, b.skuId to 1), 22_000)
        val aOrderId = detail.get("sellerOrders").get(0).get("sellerOrderId").asLong() // 판매자 주문은 판매자 ID 순 = 상점을 만든 순서
        patch("/api/v1/seller/orders/$aOrderId/ship", a.sellerToken, mapOf("carrierCode" to "04", "trackingNumber" to "T-1"))
            .andExpect { status { isOk() } }

        val summary = json(get("/api/v1/orders", user).andExpect { status { isOk() } }).get("data").get("content").get(0)
        assertEquals(listOf("PAID", "SHIPPING"), summary.get("sellerOrderStatuses").let { st -> (0 until st.size()).map { st.get(it).asString() } }.sorted())
    }
}
