package com.dogyun.kairos.cart.presentation

import com.dogyun.kairos.CommerceTestSupport
import org.springframework.test.web.servlet.ResultActionsDsl
import kotlin.test.Test
import kotlin.test.assertEquals

class CartApiTest : CommerceTestSupport() {

    private class Setup(val sellerToken: String, val productId: Long, val skuIds: List<Long>, val sellerId: Long, val companyName: String)

    private fun setUp(stock: Int = 5, salePrice: Long = 8000): Setup {
        val token = newUserToken()
        val companyName = "키로스상사"
        val sellerId = approvedSeller(token, companyName = companyName)
        val brandId = approvedBrand(token)
        val productId = onSaleProduct(token, brandId, leafCategory(), listOf(uploadImage(token)), salePrice = salePrice, stock = stock)
        return Setup(token, productId, skuIdsOf(token, productId), sellerId, companyName)
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
        postIdem("/api/v1/cart/items", user, mapOf("skuId" to setup.skuIds[0], "quantity" to 1)).expectCode(409, "SKU_NOT_ORDERABLE")
    }

    @Test
    fun 없는_SKU는_404_SKU_NOT_FOUND이고_합산_1000은_400_CART_INVALID() {
        val setup = setUp()
        val user = newUserToken()
        postIdem("/api/v1/cart/items", user, mapOf("skuId" to 999999, "quantity" to 1)).expectCode(404, "SKU_NOT_FOUND")
        addToCart(user, setup.skuIds[0], 999)
        postIdem("/api/v1/cart/items", user, mapOf("skuId" to setup.skuIds[0], "quantity" to 1)).expectCode(400, "CART_INVALID")
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
        postIdem("/api/v1/cart/items", user, mapOf("skuId" to setup.skuIds[0], "quantity" to 0)).expectCode(400, "INVALID_REQUEST")
        val itemId = addToCart(user, setup.skuIds[0], 1)
        patch("/api/v1/cart/items/$itemId", user, mapOf("quantity" to 0)).expectCode(400, "INVALID_REQUEST")
    }

    @Test
    fun 비로그인은_401() {
        get("/api/v1/cart").andExpect { status { isUnauthorized() } }
        post("/api/v1/cart/items", null, mapOf("skuId" to 1, "quantity" to 1)).andExpect { status { isUnauthorized() } }
    }

    private fun cartItems(user: String) = json(get("/api/v1/cart", user).andExpect { status { isOk() } }).get("data")

    @Test
    fun 장바구니_항목에_판매자_정보가_있다() {
        val setup = setUp()
        val user = newUserToken()
        addToCart(user, setup.skuIds[0], 1)

        val item = cartItems(user).get("items").single()

        assertEquals(setup.sellerId, item.get("sellerId").asLong())
        assertEquals("키로스상사", item.get("sellerName").asString())
    }

    @Test
    fun 정지된_판매자의_항목은_PAUSED이고_구매_가능_금액에서_빠진다() {
        val setup = setUp(salePrice = 8000)
        val user = newUserToken()
        addToCart(user, setup.skuIds[0], 2)
        suspendSeller(setup.sellerId)

        val data = cartItems(user)

        val item = data.get("items").single()
        assertEquals("PAUSED", item.get("availability").asString())
        assertEquals(16000L, item.get("lineAmount").asLong())
        assertEquals(0L, data.get("availableAmount").asLong())
        assertEquals(setup.sellerId, item.get("sellerId").asLong())
        assertEquals("키로스상사", item.get("sellerName").asString())
    }

    @Test
    fun 정지가_풀리면_다시_AVAILABLE이다() {
        val setup = setUp(salePrice = 8000)
        val user = newUserToken()
        addToCart(user, setup.skuIds[0], 1)
        suspendSeller(setup.sellerId)
        assertEquals("PAUSED", cartItems(user).get("items").single().get("availability").asString())

        unsuspendSeller(setup.sellerId)

        val data = cartItems(user)
        assertEquals("AVAILABLE", data.get("items").single().get("availability").asString())
        assertEquals(8000L, data.get("availableAmount").asLong())
    }

    @Test
    fun 장바구니가_AVAILABLE이라고_한_항목만으로_견적하면_판매자_사유로_거절되지_않는다() {
        val suspended = setUp(salePrice = 8000)
        val normal = setUp(salePrice = 9000)
        val user = newUserToken()
        addToCart(user, suspended.skuIds[0], 1)
        addToCart(user, normal.skuIds[0], 1)
        suspendSeller(suspended.sellerId)

        val items = cartItems(user).get("items")
        val availableSkus = (0 until items.size()).map { items.get(it) }
            .filter { it.get("availability").asString() == "AVAILABLE" }
            .map { it.get("skuId").asLong() }
        assertEquals(listOf(normal.skuIds[0]), availableSkus)

        post(
            "/api/v1/orders/quote", user,
            mapOf(
                "items" to availableSkus.map { mapOf("skuId" to it, "quantity" to 1) },
                "productCoupons" to emptyList<Any>(),
                "cartCouponId" to null,
                "pointsToUse" to 0,
            ),
        ).andExpect { status { isOk() } }
    }

    @Test
    fun 담기에_멱등_키가_없으면_400이다() {
        val setup = setUp()
        val user = newUserToken()

        post("/api/v1/cart/items", user, mapOf("skuId" to setup.skuIds[0], "quantity" to 1))
            .expectCode(400, "IDEMPOTENCY_KEY_REQUIRED")
    }

    @Test
    fun 같은_키로_두_번_담아도_수량은_한_번만_더해진다() {
        val setup = setUp()
        val user = newUserToken()
        val body = mapOf("skuId" to setup.skuIds[0], "quantity" to 2)

        postIdem("/api/v1/cart/items", user, body, "cart-key-1").andExpect { status { isOk() } }
        postIdem("/api/v1/cart/items", user, body, "cart-key-1").andExpect { status { isOk() } }

        assertEquals(2, cartItems(user).get("items").single().get("quantity").asInt())
    }
}
