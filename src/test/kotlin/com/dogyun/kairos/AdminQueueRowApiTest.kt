package com.dogyun.kairos

import tools.jackson.databind.JsonNode
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminQueueRowApiTest : CommerceTestSupport() {

    private val format = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?""")

    private fun now(): LocalDateTime = LocalDateTime.now(ZoneId.of("Asia/Seoul"))

    private fun rows(path: String): List<JsonNode> {
        val content = json(get(path, adminToken()).andExpect { status { isOk() } }).get("data").get("content")
        return (0 until content.size()).map { content.get(it) }
    }

    private fun assertBetween(value: String, from: LocalDateTime, to: LocalDateTime, label: String) {
        assertTrue(format.matches(value), "$label 형식: $value")
        val at = LocalDateTime.parse(value)
        // DB 시각 정밀도 차이를 감안해 1초 여유를 둔다.
        assertTrue(!at.isBefore(from.minusSeconds(1)) && !at.isAfter(to.plusSeconds(1)), "$label 범위: $value not in [$from, $to]")
    }

    @Test
    fun 관리자_큐_네_개의_행은_요청_일시를_준다() {
        val before = now()
        val token = newUserToken()
        approvedSeller(token)
        post("/api/v1/seller/brands", token, mapOf("name" to "대기브랜드-${System.nanoTime()}")).andExpect { status { isCreated() } }
        val brandId = approvedBrand(token)
        val categoryId = leafCategory()
        val fileIds = listOf(uploadImage(token))
        registerProduct(token, brandId, categoryId, fileIds)
        val onSale = onSaleProduct(token, brandId, categoryId, fileIds)
        post(
            "/api/v1/seller/products/$onSale/change-requests", token,
            mapOf("content" to productContent(categoryId, fileIds, name = "새 티셔츠"), "originalPrice" to 10000),
        ).andExpect { status { isCreated() } }
        val applicant = newUserToken()
        appliedSeller(applicant, companyName = "대기상사")
        val after = now()

        assertBetween(rows("/api/v1/admin/sellers?status=PENDING").first().get("createdAt").asString(), before, after, "seller")
        assertBetween(rows("/api/v1/admin/brands?status=PENDING").first().get("createdAt").asString(), before, after, "brand")
        assertBetween(rows("/api/v1/admin/products?status=PENDING_REVIEW").first().get("createdAt").asString(), before, after, "product")
        assertBetween(rows("/api/v1/admin/change-requests?status=PENDING").first().get("requestedAt").asString(), before, after, "changeRequest")
    }

    @Test
    fun 상품_큐와_변경_요청_큐의_행은_판매자_회사명을_준다() {
        val categoryId = leafCategory()
        val expected = mutableMapOf<Long, String>()
        for (name in listOf("가나상사", "다라상사")) {
            val token = newUserToken()
            val sellerId = approvedSeller(token, companyName = name)
            val fileIds = listOf(uploadImage(token))
            val brandId = approvedBrand(token)
            registerProduct(token, brandId, categoryId, fileIds)
            val onSale = onSaleProduct(token, brandId, categoryId, fileIds)
            post(
                "/api/v1/seller/products/$onSale/change-requests", token,
                mapOf("content" to productContent(categoryId, fileIds, name = "새 티셔츠"), "originalPrice" to 10000),
            ).andExpect { status { isCreated() } }
            expected[sellerId] = name
        }

        val products = rows("/api/v1/admin/products?status=PENDING_REVIEW")
        val changes = rows("/api/v1/admin/change-requests?status=PENDING")
        assertEquals(2, products.size)
        assertEquals(2, changes.size)
        for (row in products + changes) {
            assertEquals(expected.getValue(row.get("sellerId").asLong()), row.get("sellerName").asString())
        }
        assertEquals(expected.values.toSet(), products.map { it.get("sellerName").asString() }.toSet())
    }

    @Test
    fun seller_me와_seller_brands도_createdAt을_준다() {
        val token = newUserToken()
        approvedSeller(token)
        approvedBrand(token)

        val me = json(get("/api/v1/seller/me", token).andExpect { status { isOk() } }).get("data")
        assertTrue(format.matches(me.get("createdAt").asString()))
        val brands = json(get("/api/v1/seller/brands", token).andExpect { status { isOk() } }).get("data")
        val brand = if (brands.has("content")) brands.get("content").get(0) else brands.get(0)
        assertTrue(format.matches(brand.get("createdAt").asString()))
    }
}
