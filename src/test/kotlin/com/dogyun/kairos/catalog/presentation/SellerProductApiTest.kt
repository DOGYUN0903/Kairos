package com.dogyun.kairos.catalog.presentation

import com.dogyun.kairos.CommerceTestSupport
import org.springframework.test.web.servlet.ResultActionsDsl
import tools.jackson.databind.JsonNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SellerProductApiTest : CommerceTestSupport() {

    private fun ResultActionsDsl.expectCode(httpStatus: Int, code: String): ResultActionsDsl =
        andExpect { status { isEqualTo(httpStatus) } }.andExpect { jsonPath("$.code") { value(code) } }

    private fun JsonNode.list(): List<JsonNode> = (0 until size()).map { get(it) }

    /** 승인 판매자·승인 브랜드·소분류·이미지 2장을 갖춘 상품 하나. */
    private fun setUpProduct(): Triple<String, Long, Long> {
        val token = newUserToken()
        approvedSeller(token)
        val brandId = approvedBrand(token)
        val categoryId = leafCategory()
        val productId = registerProduct(token, brandId, categoryId, listOf(uploadImage(token), uploadImage(token)))
        return Triple(token, productId, categoryId)
    }

    @Test
    fun 상품을_등록하면_201이고_내_상세에_SKU_4개가_재고_0_ACTIVE로_있다() {
        val (token, productId, categoryId) = setUpProduct()

        val detail = sellerProductDetail(token, productId)

        assertEquals("PENDING_REVIEW", detail.get("status").asString())
        assertEquals(categoryId, detail.get("categoryId").asLong())
        assertEquals(10, detail.get("discountRate").asInt())
        assertTrue(detail.get("soldOut").asBoolean())
        val skus = detail.get("skus").list()
        assertEquals(4, skus.size)
        assertTrue(skus.all { it.get("stock").asInt() == 0 && it.get("skuStatus").asString() == "ACTIVE" && it.get("onSale").asBoolean() })
        assertEquals(setOf("블랙 / M", "블랙 / L", "화이트 / M", "화이트 / L"), skus.map { it.get("displayName").asString() }.toSet())
        assertEquals(listOf("색상", "사이즈"), detail.get("options").list().map { it.get("name").asString() })
        assertTrue(detail.get("pendingChangeRequestId").isNull)
        val page = json(get("/api/v1/seller/products?status=PENDING_REVIEW", token).andExpect { status { isOk() } }).get("data")
        assertEquals(1, page.get("totalElements").asInt())
        assertEquals("티셔츠", page.get("content").get(0).get("name").asString())
        assertTrue(page.get("content").get(0).get("thumbnailUrl").asString().contains("/files/"))
        assertEquals(0, json(get("/api/v1/seller/products?status=ON_SALE", token)).get("data").get("totalElements").asInt())
    }

    @Test
    fun SKU_재고를_바꾸면_즉시_상세에_반영된다() {
        val (token, productId, _) = setUpProduct()
        val skuId = sellerProductDetail(token, productId).get("skus").get(0).get("skuId").asLong()

        patch("/api/v1/seller/products/$productId/skus/$skuId", token, mapOf("extraPrice" to 500, "stock" to 3, "onSale" to true))
            .andExpect { status { isOk() } }

        val detail = sellerProductDetail(token, productId)
        val sku = detail.get("skus").list().single { it.get("skuId").asLong() == skuId }
        assertEquals(3, sku.get("stock").asInt())
        assertEquals(500, sku.get("extraPrice").asLong())
        assertEquals(false, detail.get("soldOut").asBoolean())
        patch("/api/v1/seller/products/$productId/skus/$skuId", token, mapOf("extraPrice" to -1, "stock" to 3, "onSale" to true))
            .expectCode(400, "INVALID_REQUEST")
        patch("/api/v1/seller/products/$productId/skus/999999", token, mapOf("extraPrice" to 0, "stock" to 3, "onSale" to true))
            .expectCode(404, "SKU_NOT_FOUND")
    }

    @Test
    fun 판매가_변경_후_할인율이_바뀐다() {
        val (token, productId, _) = setUpProduct()

        patch("/api/v1/seller/products/$productId/price", token, mapOf("salePrice" to 7500)).andExpect { status { isOk() } }

        val detail = sellerProductDetail(token, productId)
        assertEquals(7500, detail.get("salePrice").asLong())
        assertEquals(25, detail.get("discountRate").asInt())
    }

    @Test
    fun 정가는_가격_변경_API로_바꿀_수_없다() {
        val (token, productId, _) = setUpProduct()

        patch("/api/v1/seller/products/$productId/price", token, mapOf("salePrice" to 8000, "originalPrice" to 50000))
            .andExpect { status { isOk() } }

        val detail = sellerProductDetail(token, productId)
        assertEquals(10000, detail.get("originalPrice").asLong())
        assertEquals(8000, detail.get("salePrice").asLong())
    }

    @Test
    fun 다른_판매자의_상품_상세는_404() {
        val (_, productId, _) = setUpProduct()
        val other = newUserToken()
        approvedSeller(other)

        get("/api/v1/seller/products/$productId", other).expectCode(404, "PRODUCT_NOT_FOUND")
        patch("/api/v1/seller/products/$productId/price", other, mapOf("salePrice" to 1)).expectCode(404, "PRODUCT_NOT_FOUND")
        post("/api/v1/seller/products/$productId/end", other).expectCode(404, "PRODUCT_NOT_FOUND")
    }

    @Test
    fun 판매자가_아닌_회원의_상품_등록은_403() {
        val token = newUserToken()
        post(
            "/api/v1/seller/products", token,
            mapOf("brandId" to 1, "content" to productContent(1, listOf(1)), "originalPrice" to 10000, "salePrice" to 9000),
        ).expectCode(403, "FORBIDDEN")
    }

    @Test
    fun 정가보다_큰_판매가는_400_PRODUCT_INVALID_PRICE() {
        val token = newUserToken()
        approvedSeller(token)
        val brandId = approvedBrand(token)
        val categoryId = leafCategory()
        val fileId = uploadImage(token)

        post(
            "/api/v1/seller/products", token,
            mapOf("brandId" to brandId, "content" to productContent(categoryId, listOf(fileId)), "originalPrice" to 10000, "salePrice" to 10001),
        ).expectCode(400, "PRODUCT_INVALID_PRICE")
    }

    @Test
    fun 상품이_있는_소분류_삭제는_409_CATEGORY_HAS_PRODUCTS() {
        val (_, _, categoryId) = setUpProduct()

        delete("/api/v1/admin/categories/$categoryId", adminToken()).expectCode(409, "CATEGORY_HAS_PRODUCTS")
    }

    @Test
    fun 상세_이미지_URL은_files_경로다() {
        val (token, productId, _) = setUpProduct()

        val images = sellerProductDetail(token, productId).get("images").list()

        assertEquals(2, images.size)
        assertTrue(images.all { it.get("url").asString().contains("/files/") })
        assertEquals(listOf(true, false), images.map { it.get("isMain").asBoolean() })
    }

    @Test
    fun 심사_대기_상품은_중지할_수_없고_직접_수정하면_다시_심사_대기다() {
        val (token, productId, categoryId) = setUpProduct()
        post("/api/v1/seller/products/$productId/pause", token).expectCode(409, "PRODUCT_INVALID_STATE_TRANSITION")
        val before = sellerProductDetail(token, productId)
        val color = before.get("options").get(0)
        val black = color.get("values").get(0)
        val keptSkuIds = before.get("skus").list().filter { it.get("displayName").asString().startsWith("블랙") }.map { it.get("skuId").asLong() }.toSet()

        put(
            "/api/v1/seller/products/$productId", token,
            productContent(
                categoryId, listOf(uploadImage(token)),
                options = listOf(
                    mapOf(
                        "optionId" to color.get("optionId").asLong(), "name" to "색상",
                        "values" to listOf(mapOf("valueId" to black.get("valueId").asLong(), "name" to "검정")),
                    ),
                    mapOf("optionId" to before.get("options").get(1).get("optionId").asLong(), "name" to "사이즈", "values" to listOf(mapOf("name" to "M"), mapOf("name" to "L"))),
                ),
                name = "반팔 티셔츠",
            ),
        ).andExpect { status { isOk() } }

        val after = sellerProductDetail(token, productId)
        assertEquals("반팔 티셔츠", after.get("name").asString())
        assertEquals("PENDING_REVIEW", after.get("status").asString())
        assertEquals(1, after.get("images").size())
        val skus = after.get("skus").list()
        assertEquals(4, skus.count { it.get("skuStatus").asString() == "DISCONTINUED" })
        assertEquals(2, skus.count { it.get("skuStatus").asString() == "ACTIVE" })
        assertTrue(skus.filter { it.get("skuStatus").asString() == "ACTIVE" }.all { it.get("displayName").asString().startsWith("검정 / ") })
        assertTrue(skus.filter { it.get("skuStatus").asString() == "DISCONTINUED" }.map { it.get("skuId").asLong() }.containsAll(keptSkuIds))
        // 남의 옵션 ID 를 보내면 400
        put(
            "/api/v1/seller/products/$productId", token,
            productContent(categoryId, listOf(uploadImage(token)), options = listOf(mapOf("optionId" to 999999, "name" to "색상", "values" to listOf(mapOf("name" to "검정"))))),
        ).expectCode(400, "PRODUCT_INVALID")
    }
}
