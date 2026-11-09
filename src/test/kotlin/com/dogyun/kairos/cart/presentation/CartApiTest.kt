package com.dogyun.kairos.cart.presentation

import com.dogyun.kairos.CommerceTestSupport
import org.springframework.test.web.servlet.ResultActionsDsl
import kotlin.test.Test
import kotlin.test.assertEquals

class CartApiTest : CommerceTestSupport() {

    private class Setup(val sellerToken: String, val productId: Long, val skuIds: List<Long>)

    private fun setUp(stock: Int = 5, salePrice: Long = 8000): Setup {
        val token = newUserToken()
        approvedSeller(token)
        val brandId = approvedBrand(token)
        val productId = onSaleProduct(token, brandId, leafCategory(), listOf(uploadImage(token)), salePrice = salePrice, stock = stock)
        return Setup(token, productId, skuIdsOf(token, productId))
    }

    private fun ResultActionsDsl.expectCode(status: Int, code: String) {
        andExpect {
            status { isEqualTo(status) }
            jsonPath("$.code") { value(code) }
        }
    }

    @Test
    fun 담고_수량_바꾸고_조회하면_현재_판매가로_금액이_계산된다() {
        val setup = setUp(salePrice = 8000)
        val user = newUserToken()
        val itemId = addToCart(user, setup.skuIds[0], 2)
        addToCart(user, setup.skuIds[0], 1)
        patch("/api/v1/cart/items/$itemId", user, mapOf("quantity" to 4)).andExpect { status { isOk() } }

        val data = json(get("/api/v1/cart", user).andExpect { status { isOk() } }).get("data")

        val item = data.get("items").single()
        assertEquals(itemId, item.get("cartItemId").asLong())
        assertEquals(8000L, item.get("unitPrice").asLong())
        assertEquals(4, item.get("quantity").asInt())
        assertEquals(32000L, item.get("lineAmount").asLong())
        assertEquals("AVAILABLE", item.get("availability").asString())
        assertEquals("티셔츠", item.get("productName").asString())
        assertEquals(32000L, data.get("availableAmount").asLong())

        delete("/api/v1/cart/items/$itemId", user).andExpect { status { isNoContent() } }
        assertEquals(0, json(get("/api/v1/cart", user)).get("data").get("items").size())
        delete("/api/v1/cart/items/$itemId", user).expectCode(404, "CART_ITEM_NOT_FOUND")
    }

    @Test
    fun 판매자가_판매가를_바꾸면_장바구니_금액도_바뀐다() {
        val setup = setUp(salePrice = 8000)
        val user = newUserToken()
        addToCart(user, setup.skuIds[0], 2)

        patch("/api/v1/seller/products/${setup.productId}/price", setup.sellerToken, mapOf("salePrice" to 7000))
            .andExpect { status { isOk() } }

        val data = json(get("/api/v1/cart", user)).get("data")
        assertEquals(7000L, data.get("items").single().get("unitPrice").asLong())
        assertEquals(14000L, data.get("availableAmount").asLong())
    }

    @Test
    fun 재고가_0이_되면_SOLD_OUT으로_표시되고_availableAmount에서_빠진다() {
        val setup = setUp(salePrice = 8000)
        val user = newUserToken()
        addToCart(user, setup.skuIds[0], 1)
        addToCart(user, setup.skuIds[1], 1)

        patch(
            "/api/v1/seller/products/${setup.productId}/skus/${setup.skuIds[0]}", setup.sellerToken,
            mapOf("extraPrice" to 0, "stock" to 0, "onSale" to true),
        ).andExpect { status { isOk() } }

        val data = json(get("/api/v1/cart", user)).get("data")
        val bySku = (0 until data.get("items").size()).map { data.get("items").get(it) }.associateBy { it.get("skuId").asLong() }
        assertEquals("SOLD_OUT", bySku.getValue(setup.skuIds[0]).get("availability").asString())
        assertEquals("AVAILABLE", bySku.getValue(setup.skuIds[1]).get("availability").asString())
        assertEquals(8000L, data.get("availableAmount").asLong())
        post("/api/v1/cart/items", user, mapOf("skuId" to setup.skuIds[0], "quantity" to 1)).expectCode(409, "SKU_NOT_ORDERABLE")
    }

    @Test
    fun 없는_SKU는_404_SKU_NOT_FOUND이고_합산_1000은_400_CART_INVALID() {
        val setup = setUp()
        val user = newUserToken()
        post("/api/v1/cart/items", user, mapOf("skuId" to 999999, "quantity" to 1)).expectCode(404, "SKU_NOT_FOUND")
        addToCart(user, setup.skuIds[0], 999)
        post("/api/v1/cart/items", user, mapOf("skuId" to setup.skuIds[0], "quantity" to 1)).expectCode(400, "CART_INVALID")
    }

    @Test
    fun 다른_회원의_항목은_404() {
        val setup = setUp()
        val itemId = addToCart(newUserToken(), setup.skuIds[0], 1)
        val other = newUserToken()
        patch("/api/v1/cart/items/$itemId", other, mapOf("quantity" to 2)).expectCode(404, "CART_ITEM_NOT_FOUND")
        delete("/api/v1/cart/items/$itemId", other).expectCode(404, "CART_ITEM_NOT_FOUND")
    }

    @Test
    fun 수량_0은_400() {
        val setup = setUp()
        val user = newUserToken()
        post("/api/v1/cart/items", user, mapOf("skuId" to setup.skuIds[0], "quantity" to 0)).expectCode(400, "INVALID_REQUEST")
        val itemId = addToCart(user, setup.skuIds[0], 1)
        patch("/api/v1/cart/items/$itemId", user, mapOf("quantity" to 0)).expectCode(400, "INVALID_REQUEST")
    }

    @Test
    fun 비로그인은_401() {
        get("/api/v1/cart").andExpect { status { isUnauthorized() } }
        post("/api/v1/cart/items", null, mapOf("skuId" to 1, "quantity" to 1)).andExpect { status { isUnauthorized() } }
    }
}
