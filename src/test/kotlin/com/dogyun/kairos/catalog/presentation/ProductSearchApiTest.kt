package com.dogyun.kairos.catalog.presentation

import com.dogyun.kairos.CommerceTestSupport
import org.springframework.test.web.servlet.ResultActionsDsl
import tools.jackson.databind.JsonNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 비로그인 사용자 상품 목록·검색·상세. 노출 조건은 상품 ON_SALE + 브랜드 APPROVED + 판매자 APPROVED. */
class ProductSearchApiTest : CommerceTestSupport() {

    private fun ResultActionsDsl.expectCode(httpStatus: Int, code: String): ResultActionsDsl =
        andExpect { status { isEqualTo(httpStatus) } }.andExpect { jsonPath("$.code") { value(code) } }

    private fun JsonNode.list(): List<JsonNode> = (0 until size()).map { get(it) }

    private fun listPage(query: String = ""): JsonNode =
        json(get("/api/v1/products$query").andExpect { status { isOk() } }).get("data")

    private fun listIds(query: String = ""): List<Long> = listPage(query).get("content").list().map { it.get("productId").asLong() }

    private fun detail(productId: Long): JsonNode =
        json(get("/api/v1/products/$productId").andExpect { status { isOk() } }).get("data")

    /** MockMvc 는 쿼리 문자열을 스스로 인코딩하므로 원문을 그대로 넣는다. */
    private fun encode(keyword: String) = keyword

    /** 승인 판매자·승인 브랜드·소분류. */
    private class Scene(val token: String, val sellerId: Long, val brandId: Long, val categoryId: Long)

    private fun scene(): Scene {
        val token = newUserToken()
        val sellerId = approvedSeller(token)
        val brandId = approvedBrand(token)
        return Scene(token, sellerId, brandId, leafCategory())
    }

    private fun onSale(s: Scene, categoryId: Long = s.categoryId, salePrice: Long = 8000, originalPrice: Long = 10000, stock: Int = 5): Long =
        onSaleProduct(s.token, s.brandId, categoryId, listOf(uploadImage(s.token)), originalPrice, salePrice, stock)

    private fun onSaleNamed(s: Scene, name: String, brandId: Long = s.brandId): Long {
        val created = post(
            "/api/v1/seller/products", s.token,
            mapOf(
                "brandId" to brandId,
                "content" to productContent(s.categoryId, listOf(uploadImage(s.token)), name = name),
                "originalPrice" to 10000,
                "salePrice" to 8000,
            ),
        ).andExpect { status { isCreated() } }
        val productId = json(created).get("data").get("productId").asLong()
        approveProduct(productId)
        return productId
    }

    @Test
    fun 목록에는_ON_SALE이고_브랜드와_판매자가_승인된_상품만_나온다() {
        val s = scene()
        val onSaleId = onSale(s)
        val pendingId = registerProduct(s.token, s.brandId, s.categoryId, listOf(uploadImage(s.token)))
        val pausedId = onSale(s).also { post("/api/v1/seller/products/$it/pause", s.token).andExpect { status { isOk() } } }
        val endedId = onSale(s).also { post("/api/v1/seller/products/$it/end", s.token).andExpect { status { isOk() } } }

        val ids = listIds()

        assertEquals(listOf(onSaleId), ids)
        get("/api/v1/products/$pendingId").expectCode(404, "PRODUCT_NOT_FOUND")
        get("/api/v1/products/$pausedId").expectCode(404, "PRODUCT_NOT_FOUND")
        get("/api/v1/products/$endedId").expectCode(404, "PRODUCT_NOT_FOUND")
        assertEquals(onSaleId, detail(onSaleId).get("productId").asLong())
    }

    @Test
    fun 정지된_판매자의_상품은_목록과_상세에서_사라지고_해제하면_다시_보인다() {
        val s = scene()
        val productId = onSale(s)
        assertEquals(listOf(productId), listIds())

        suspendSeller(s.sellerId)
        assertEquals(emptyList(), listIds())
        get("/api/v1/products/$productId").expectCode(404, "PRODUCT_NOT_FOUND")

        unsuspendSeller(s.sellerId)
        assertEquals(listOf(productId), listIds())
        detail(productId)
    }

    @Test
    fun 정지된_판매자의_상품은_상세도_404다() {
        val s = scene()
        val productId = onSale(s)
        detail(productId)

        suspendSeller(s.sellerId)

        get("/api/v1/products/$productId").expectCode(404, "PRODUCT_NOT_FOUND")
    }

    @Test
    fun 브랜드나_판매자_상태를_JdbcTemplate으로_바꾸면_상품이_목록과_상세에서_사라진다() {
        val s = scene()
        val productId = onSale(s)
        assertEquals(1, listPage().get("totalElements").asInt())

        jdbcTemplate.update("update brand set brand_status = 'REJECTED' where id = ?", s.brandId)
        assertEquals(0, listPage().get("totalElements").asInt())
        get("/api/v1/products/$productId").expectCode(404, "PRODUCT_NOT_FOUND")

        jdbcTemplate.update("update brand set brand_status = 'APPROVED' where id = ?", s.brandId)
        assertEquals(1, listPage().get("totalElements").asInt())

        jdbcTemplate.update("update seller set seller_status = 'SUSPENDED' where id = ?", s.sellerId)
        assertEquals(0, listPage().get("totalElements").asInt())
        get("/api/v1/products/$productId").expectCode(404, "PRODUCT_NOT_FOUND")
    }

    @Test
    fun 대분류로_필터하면_하위_소분류_상품이_모두_나온다() {
        val s = scene()
        val rootId = createCategory(null, "의류", 1)
        val leafA = createCategory(rootId, "상의", 1)
        val leafB = createCategory(rootId, "하의", 2)
        val inA = onSale(s, categoryId = leafA)
        val inB = onSale(s, categoryId = leafB)
        val elsewhere = onSale(s)

        assertEquals(setOf(inA, inB), listIds("?categoryId=$rootId").toSet())
        assertEquals(listOf(inA), listIds("?categoryId=$leafA"))
        assertEquals(listOf(elsewhere), listIds("?categoryId=${s.categoryId}"))
        get("/api/v1/products?categoryId=999999").expectCode(404, "CATEGORY_NOT_FOUND")
    }

    @Test
    fun 브랜드와_가격대_필터() {
        val s = scene()
        val otherBrand = approvedBrand(s.token)
        val cheap = onSale(s, salePrice = 4999)
        val low = onSale(s, salePrice = 5000)
        val high = onSale(s, salePrice = 9000)
        val expensive = onSale(s, salePrice = 9001)
        val otherBrandMid = onSaleProduct(s.token, otherBrand, s.categoryId, listOf(uploadImage(s.token)), 10000, 7000)

        assertEquals(setOf(low, high, otherBrandMid), listIds("?minPrice=5000&maxPrice=9000").toSet())
        assertEquals(setOf(low, high), listIds("?brandId=${s.brandId}&minPrice=5000&maxPrice=9000").toSet())
        assertEquals(listOf(otherBrandMid), listIds("?brandId=$otherBrand"))
        assertEquals(setOf(cheap, low, high, expensive, otherBrandMid), listIds().toSet())
        get("/api/v1/products?minPrice=9000&maxPrice=5000").expectCode(400, "INVALID_REQUEST")
    }

    @Test
    fun 검색어는_상품명과_브랜드명_부분_일치다() {
        val s = scene()
        val brandId = approvedBrand(s.token, name = "카이로스웨어")
        val byName = onSaleNamed(s, "여름 린넨 셔츠")
        val byBrand = onSaleNamed(s, "청바지", brandId = brandId)
        onSaleNamed(s, "양말")

        assertEquals(listOf(byName), listIds("?keyword=${encode("린넨")}"))
        assertEquals(listOf(byBrand), listIds("?keyword=${encode("로스웨")}"))
        assertEquals(3, listIds("?keyword=${encode("   ")}").size)
        assertEquals(0, listIds("?keyword=${encode("없는말")}").size)
    }

    @Test
    fun 검색어의_퍼센트와_언더스코어는_문자로_취급된다() {
        val s = scene()
        onSaleNamed(s, "티셔츠")
        val underscore = onSaleNamed(s, "A_B 세일")
        assertEquals(2, listIds().size)

        assertEquals(0, listIds("?keyword=${encode("%")}").size)
        assertEquals(0, listIds("?keyword=${encode("티_츠")}").size)
        assertEquals(0, listIds("?keyword=${encode("\\")}").size)
        assertEquals(listOf(underscore), listIds("?keyword=${encode("A_B")}"))
    }

    @Test
    fun 정렬_최신순_가격순_인기순() {
        val s = scene()
        val first = onSale(s, salePrice = 7000)
        val second = onSale(s, salePrice = 9000)
        val third = onSale(s, salePrice = 8000)
        jdbcTemplate.update("update product set sales_count = 10 where id = ?", first)
        jdbcTemplate.update("update product set sales_count = 10 where id = ?", second)

        assertEquals(listOf(third, second, first), listIds("?sort=LATEST"))
        assertEquals(listOf(third, second, first), listIds())
        assertEquals(listOf(first, third, second), listIds("?sort=PRICE_ASC"))
        assertEquals(listOf(second, third, first), listIds("?sort=PRICE_DESC"))
        assertEquals(listOf(second, first, third), listIds("?sort=POPULAR"))
    }

    @Test
    fun 최신순은_onSaleAt_기준이라_일시중지_해제해도_순서가_바뀌지_않는다() {
        val s = scene()
        val first = onSale(s)
        val second = onSale(s)
        post("/api/v1/seller/products/$first/pause", s.token).andExpect { status { isOk() } }
        post("/api/v1/seller/products/$first/resume", s.token).andExpect { status { isOk() } }

        assertEquals(listOf(second, first), listIds("?sort=LATEST"))
    }

    @Test
    fun 페이징_size_20_기본과_hasNext() {
        val s = scene()
        repeat(25) { onSale(s) }

        val page0 = listPage()
        assertEquals(20, page0.get("content").size())
        assertEquals(20, page0.get("size").asInt())
        assertEquals(25, page0.get("totalElements").asInt())
        assertEquals(2, page0.get("totalPages").asInt())
        assertTrue(page0.get("hasNext").asBoolean())
        val page1 = listPage("?page=1")
        assertEquals(5, page1.get("content").size())
        assertFalse(page1.get("hasNext").asBoolean())
    }

    @Test
    fun size_101은_400() {
        get("/api/v1/products?size=101").expectCode(400, "INVALID_REQUEST")
        get("/api/v1/products?size=0").expectCode(400, "INVALID_REQUEST")
        get("/api/v1/products?page=-1").expectCode(400, "INVALID_REQUEST")
        listPage("?size=100")
    }

    @Test
    fun 모든_ACTIVE_SKU_재고가_0이면_soldOut_true이고_판매_여부를_끈_SKU는_제외된다() {
        val s = scene()
        val productId = onSale(s, stock = 0)
        assertTrue(listPage().get("content").get(0).get("soldOut").asBoolean())
        assertTrue(detail(productId).get("soldOut").asBoolean())

        val skuId = sellerProductDetail(s.token, productId).get("skus").get(0).get("skuId").asLong()
        patch("/api/v1/seller/products/$productId/skus/$skuId", s.token, mapOf("extraPrice" to 0, "stock" to 5, "onSale" to false))
            .andExpect { status { isOk() } }
        assertTrue(listPage().get("content").get(0).get("soldOut").asBoolean())
        assertTrue(detail(productId).get("soldOut").asBoolean())

        patch("/api/v1/seller/products/$productId/skus/$skuId", s.token, mapOf("extraPrice" to 0, "stock" to 5, "onSale" to true))
            .andExpect { status { isOk() } }
        assertFalse(listPage().get("content").get(0).get("soldOut").asBoolean())
        val detail = detail(productId)
        assertFalse(detail.get("soldOut").asBoolean())
        val skus = detail.get("skus").list()
        assertEquals(1, skus.count { !it.get("soldOut").asBoolean() })
        assertEquals(3, skus.count { it.get("soldOut").asBoolean() })
    }

    @Test
    fun 상세에는_재고_숫자가_없고_DISCONTINUED_SKU는_빠진다() {
        val s = scene()
        val productId = onSale(s)
        val skuId = sellerProductDetail(s.token, productId).get("skus").get(0).get("skuId").asLong()
        jdbcTemplate.update("update sku set sku_status = 'DISCONTINUED' where id = ?", skuId)

        get("/api/v1/products/$productId")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.data.skus[0].stock") { doesNotExist() } }
            .andExpect { jsonPath("$.data.skus[0].onSale") { doesNotExist() } }
            .andExpect { jsonPath("$.data.skus[0].skuStatus") { doesNotExist() } }
        val detail = detail(productId)
        val skus = detail.get("skus").list()
        assertEquals(3, skus.size)
        assertTrue(skus.none { it.get("skuId").asLong() == skuId })
        assertEquals(setOf("블랙 / L", "화이트 / M", "화이트 / L"), skus.map { it.get("displayName").asString() }.toSet())
        assertEquals(listOf("색상", "사이즈"), detail.get("options").list().map { it.get("name").asString() })
        assertEquals(s.brandId, detail.get("brandId").asLong())
        assertEquals(s.categoryId, detail.get("categoryId").asLong())
        assertEquals("티셔츠", detail.get("name").asString())
        assertEquals(1, detail.get("imageUrls").size())
        assertTrue(detail.get("imageUrls").get(0).asString().contains("/files/"))
    }

    @Test
    fun 할인율은_내림이다() {
        val s = scene()
        val productId = onSale(s, originalPrice = 30000, salePrice = 20000) // 33.33… → 33

        assertEquals(33, listPage().get("content").get(0).get("discountRate").asInt())
        val detail = detail(productId)
        assertEquals(33, detail.get("discountRate").asInt())
        assertEquals(30000, detail.get("originalPrice").asLong())
        assertEquals(20000, detail.get("salePrice").asLong())
    }

    @Test
    fun 목록_썸네일은_대표_이미지_URL이다() {
        val s = scene()
        val productId = onSaleProduct(s.token, s.brandId, s.categoryId, listOf(uploadImage(s.token), uploadImage(s.token)))
        val images = sellerProductDetail(s.token, productId).get("images").list()
        val mainUrl = images.single { it.get("isMain").asBoolean() }.get("url").asString()

        val row = listPage().get("content").get(0)

        assertEquals(mainUrl, row.get("thumbnailUrl").asString())
        assertTrue(row.get("brandName").asString().startsWith("브랜드-"))
        assertEquals("티셔츠", row.get("name").asString())
        assertEquals(listOf(mainUrl, images[1].get("url").asString()), detail(productId).get("imageUrls").list().map { it.asString() })
    }
}
