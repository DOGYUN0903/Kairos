package com.dogyun.kairos.catalog.presentation

import com.dogyun.kairos.CommerceTestSupport
import org.springframework.test.web.servlet.ResultActionsDsl
import tools.jackson.databind.JsonNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AdminProductApiTest : CommerceTestSupport() {

    private fun ResultActionsDsl.expectCode(httpStatus: Int, code: String): ResultActionsDsl =
        andExpect { status { isEqualTo(httpStatus) } }.andExpect { jsonPath("$.code") { value(code) } }

    private fun JsonNode.list(): List<JsonNode> = (0 until size()).map { get(it) }

    private class Fixture(val token: String, val brandId: Long, val categoryId: Long, val fileIds: List<Long>)

    private fun setUp(): Fixture {
        val token = newUserToken()
        approvedSeller(token)
        return Fixture(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token), uploadImage(token)))
    }

    private fun Fixture.register(): Long = registerProduct(token, brandId, categoryId, fileIds)

    @Test
    fun 승인하면_판매자_상세가_ON_SALE이고_onSaleAt이_있다() {
        val f = setUp()
        val productId = f.register()
        assertTrue(sellerProductDetail(f.token, productId).get("onSaleAt").isNull)

        post("/api/v1/admin/products/$productId/approve", adminToken()).andExpect { status { isOk() } }

        val detail = sellerProductDetail(f.token, productId)
        assertEquals("ON_SALE", detail.get("status").asString())
        assertNotNull(detail.get("onSaleAt").asString())
        post("/api/v1/admin/products/$productId/approve", adminToken()).expectCode(409, "PRODUCT_INVALID_STATE_TRANSITION")
        post("/api/v1/admin/products/999999/approve", adminToken()).expectCode(404, "PRODUCT_NOT_FOUND")
    }

    @Test
    fun 반려_후_판매자가_고치면_다시_PENDING_REVIEW다() {
        val f = setUp()
        val productId = f.register()

        post("/api/v1/admin/products/$productId/reject", adminToken(), mapOf("reason" to "이미지 부족"))
            .andExpect { status { isOk() } }

        val rejected = sellerProductDetail(f.token, productId)
        assertEquals("REJECTED", rejected.get("status").asString())
        assertEquals("이미지 부족", rejected.get("rejectReason").asString())
        put("/api/v1/seller/products/$productId", f.token, productContent(f.categoryId, f.fileIds, name = "고친 티셔츠"))
            .andExpect { status { isOk() } }
        val fixed = sellerProductDetail(f.token, productId)
        assertEquals("PENDING_REVIEW", fixed.get("status").asString())
        assertTrue(fixed.get("rejectReason").isNull)
        assertEquals("고친 티셔츠", fixed.get("name").asString())
        post("/api/v1/admin/products/$productId/reject", adminToken(), mapOf("reason" to " ")).expectCode(400, "INVALID_REQUEST")
    }

    @Test
    fun 상태_필터_심사_목록() {
        val f = setUp()
        val pending = f.register()
        val approved = f.register()
        approveProduct(approved)

        val pendingPage = json(get("/api/v1/admin/products?status=PENDING_REVIEW", adminToken()).andExpect { status { isOk() } }).get("data")
        val onSalePage = json(get("/api/v1/admin/products?status=ON_SALE", adminToken())).get("data")
        val all = json(get("/api/v1/admin/products", adminToken())).get("data")

        assertEquals(listOf(pending), pendingPage.get("content").list().map { it.get("productId").asLong() })
        assertEquals(listOf(approved), onSalePage.get("content").list().map { it.get("productId").asLong() })
        assertEquals(2, all.get("totalElements").asInt())
        val item = pendingPage.get("content").get(0)
        assertEquals(10000, item.get("originalPrice").asLong())
        assertEquals(9000, item.get("salePrice").asLong())
        assertTrue(item.get("thumbnailUrl").asString().contains("/files/"))
        assertFalse(item.get("brandName").asString().isEmpty())
        get("/api/v1/admin/products?size=101", adminToken()).expectCode(400, "INVALID_REQUEST")
        val detail = json(get("/api/v1/admin/products/$pending", adminToken()).andExpect { status { isOk() } }).get("data")
        assertEquals("PENDING_REVIEW", detail.get("status").asString())
        assertEquals(4, detail.get("skus").size())
        get("/api/v1/admin/products/999999", adminToken()).expectCode(404, "PRODUCT_NOT_FOUND")
    }

    @Test
    fun 승인과_반려는_심사_이력을_남긴다() {
        val f = setUp()
        val rejected = f.register()
        val approved = f.register()

        post("/api/v1/admin/products/$rejected/reject", adminToken(), mapOf("reason" to "사유")).andExpect { status { isOk() } }
        approveProduct(approved)

        val rows = jdbcTemplate.queryForList(
            "select target_type, target_id, result, reason from product_review_history order by id",
        )
        assertEquals(2, rows.size)
        assertEquals(listOf("PRODUCT", "PRODUCT"), rows.map { it["target_type"] })
        assertEquals(listOf(rejected, approved), rows.map { (it["target_id"] as Number).toLong() })
        assertEquals(listOf("REJECTED", "APPROVED"), rows.map { it["result"] })
        assertEquals("사유", rows[0]["reason"])
    }

    @Test
    fun 판매자_토큰으로_승인하면_403() {
        val f = setUp()
        val productId = f.register()

        post("/api/v1/admin/products/$productId/approve", f.token).andExpect { status { isForbidden() } }
        post("/api/v1/admin/products/$productId/reject", f.token, mapOf("reason" to "사유")).andExpect { status { isForbidden() } }
        get("/api/v1/admin/products", f.token).andExpect { status { isForbidden() } }
        assertEquals("PENDING_REVIEW", sellerProductDetail(f.token, productId).get("status").asString())
    }
}
