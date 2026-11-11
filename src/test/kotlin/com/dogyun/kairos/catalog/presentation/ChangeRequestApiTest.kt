package com.dogyun.kairos.catalog.presentation

import com.dogyun.kairos.CommerceTestSupport
import org.springframework.test.web.servlet.ResultActionsDsl
import tools.jackson.databind.JsonNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChangeRequestApiTest : CommerceTestSupport() {

    private fun ResultActionsDsl.expectCode(httpStatus: Int, code: String): ResultActionsDsl =
        andExpect { status { isEqualTo(httpStatus) } }.andExpect { jsonPath("$.code") { value(code) } }

    private fun JsonNode.list(): List<JsonNode> = (0 until size()).map { get(it) }

    /** 승인 판매자·승인 브랜드·소분류·이미지 2장을 갖춘 ON_SALE 상품(SKU 4개, 재고 5). */
    private class Fixture(val token: String, val productId: Long, val categoryId: Long, val fileIds: List<Long>)

    private fun onSaleFixture(): Fixture {
        val token = newUserToken()
        approvedSeller(token)
        val categoryId = leafCategory()
        val fileIds = listOf(uploadImage(token), uploadImage(token))
        val productId = onSaleProduct(token, approvedBrand(token), categoryId, fileIds)
        return Fixture(token, productId, categoryId, fileIds)
    }

    private fun Fixture.submit(body: Map<String, Any?> = sameContentRequest(name = "새 티셔츠")): Long =
        json(post("/api/v1/seller/products/$productId/change-requests", token, body).andExpect { status { isCreated() } })
            .get("data").get("changeRequestId").asLong()

    private fun Fixture.sameContentRequest(name: String, originalPrice: Long = 10000): Map<String, Any?> =
        mapOf("content" to productContent(categoryId, fileIds, name = name), "originalPrice" to originalPrice)

    /** 현재 옵션·값 ID 를 그대로 보내는(=옵션은 안 바뀌는) 요청. */
    private fun Fixture.keepingOptions(name: String, originalPrice: Long = 10000): Map<String, Any?> {
        val options = sellerProductDetail(token, productId).get("options").list().map { option ->
            mapOf(
                "optionId" to option.get("optionId").asLong(), "name" to option.get("name").asString(),
                "values" to option.get("values").list().map { mapOf("valueId" to it.get("valueId").asLong(), "name" to it.get("name").asString()) },
            )
        }
        return mapOf("content" to productContent(categoryId, fileIds, options = options, name = name), "originalPrice" to originalPrice)
    }

    /** 색상[블랙(유지)·레드(새 값)] × 사이즈[M·L(유지)], 이름 "새 티셔츠". */
    private fun Fixture.blackRedRequest(): Map<String, Any?> {
        val options = sellerProductDetail(token, productId).get("options")
        val color = options.get(0)
        val size = options.get(1)
        return mapOf(
            "content" to productContent(
                categoryId, fileIds,
                options = listOf(
                    mapOf(
                        "optionId" to color.get("optionId").asLong(), "name" to "색상",
                        "values" to listOf(mapOf("valueId" to color.get("values").get(0).get("valueId").asLong(), "name" to "블랙"), mapOf("name" to "레드")),
                    ),
                    mapOf(
                        "optionId" to size.get("optionId").asLong(), "name" to "사이즈",
                        "values" to size.get("values").list().map { mapOf("valueId" to it.get("valueId").asLong(), "name" to it.get("name").asString()) },
                    ),
                ),
                name = "새 티셔츠",
            ),
            "originalPrice" to 10000,
        )
    }

    @Test
    fun 변경_요청_목록_행은_바뀐_항목_changedFields를_준다() {
        val f = onSaleFixture()
        f.submit(f.keepingOptions(name = "새 티셔츠", originalPrice = 16000))

        val row = json(get("/api/v1/admin/change-requests?status=PENDING", adminToken()).andExpect { status { isOk() } })
            .get("data").get("content").get(0)

        assertEquals(listOf("NAME", "ORIGINAL_PRICE"), row.get("changedFields").list().map { it.asString() })
    }

    @Test
    fun 옵션_값을_바꾼_변경_요청의_changedFields에_OPTIONS가_들어간다() {
        val f = onSaleFixture()
        f.submit(f.blackRedRequest())

        val row = json(get("/api/v1/admin/change-requests?status=PENDING", adminToken())).get("data").get("content").get(0)

        assertEquals(listOf("NAME", "OPTIONS"), row.get("changedFields").list().map { it.asString() })
    }

    @Test
    fun 내용이_같은_변경_요청의_changedFields는_빈_배열이다() {
        val f = onSaleFixture()
        f.submit(f.keepingOptions(name = "티셔츠"))

        val row = json(get("/api/v1/admin/change-requests?status=PENDING", adminToken())).get("data").get("content").get(0)

        assertEquals(0, row.get("changedFields").size())
    }

    @Test
    fun 변경_요청_중에도_판매자_상세는_기존_내용이고_pendingChangeRequestId가_있다() {
        val f = onSaleFixture()

        val id = f.submit(f.sameContentRequest(name = "새 티셔츠", originalPrice = 16000))

        val detail = sellerProductDetail(f.token, f.productId)
        assertEquals("티셔츠", detail.get("name").asString())
        assertEquals(10000, detail.get("originalPrice").asLong())
        assertEquals("ON_SALE", detail.get("status").asString())
        assertEquals(id, detail.get("pendingChangeRequestId").asLong())
        assertEquals(id, json(get("/api/v1/admin/products/${f.productId}", adminToken())).get("data").get("pendingChangeRequestId").asLong())
        val request = json(get("/api/v1/admin/change-requests/$id", adminToken()).andExpect { status { isOk() } }).get("data")
        assertEquals("PENDING", request.get("status").asString())
        assertTrue(request.get("requestedAt").asString().matches(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?""")), "상세도 요청 일시를 준다")
        assertEquals(16000, request.get("originalPrice").asLong())
        assertEquals("새 티셔츠", request.get("content").get("name").asString())
        assertTrue(request.get("content").get("images").get(0).get("url").asString().contains("/files/"))
        assertEquals(2, request.get("content").get("options").size())
        val page = json(get("/api/v1/admin/change-requests?status=PENDING", adminToken()).andExpect { status { isOk() } }).get("data")
        assertEquals(listOf(id), page.get("content").list().map { it.get("changeRequestId").asLong() })
        assertEquals("새 티셔츠", page.get("content").get(0).get("name").asString())
        get("/api/v1/admin/change-requests?size=101", adminToken()).expectCode(400, "INVALID_REQUEST")
        get("/api/v1/admin/change-requests/999999", adminToken()).expectCode(404, "CHANGE_REQUEST_NOT_FOUND")
    }

    @Test
    fun 승인_후_판매자_상세_이름과_SKU가_바뀐다() {
        val f = onSaleFixture()
        val before = sellerProductDetail(f.token, f.productId)
        val skusBefore = before.get("skus").list()
        val keptIds = skusBefore.filter { it.get("displayName").asString().startsWith("블랙") }.map { it.get("skuId").asLong() }.toSet()
        val goneIds = skusBefore.filter { it.get("displayName").asString().startsWith("화이트") }.map { it.get("skuId").asLong() }.toSet()
        val id = f.submit(f.blackRedRequest())

        post("/api/v1/admin/change-requests/$id/approve", adminToken()).andExpect { status { isOk() } }

        val after = sellerProductDetail(f.token, f.productId)
        assertEquals("새 티셔츠", after.get("name").asString())
        assertEquals("ON_SALE", after.get("status").asString())
        assertEquals(before.get("onSaleAt").asString(), after.get("onSaleAt").asString())
        assertTrue(after.get("pendingChangeRequestId").isNull)
        val skus = after.get("skus").list()
        val kept = skus.filter { it.get("skuId").asLong() in keptIds }
        assertEquals(2, kept.size)
        assertTrue(kept.all { it.get("skuStatus").asString() == "ACTIVE" && it.get("stock").asInt() == 5 && it.get("displayName").asString().startsWith("블랙 / ") })
        val gone = skus.filter { it.get("skuId").asLong() in goneIds }
        assertEquals(2, gone.size)
        assertTrue(gone.all { it.get("skuStatus").asString() == "DISCONTINUED" })
        val red = skus.filter { it.get("displayName").asString().startsWith("레드 / ") }
        assertEquals(2, red.size)
        assertTrue(red.all { it.get("skuStatus").asString() == "ACTIVE" && it.get("stock").asInt() == 0 })
        assertEquals(6, skus.size)
        assertEquals("APPROVED", json(get("/api/v1/admin/change-requests/$id", adminToken())).get("data").get("status").asString())
        post("/api/v1/admin/change-requests/$id/approve", adminToken()).expectCode(409, "CHANGE_REQUEST_INVALID_STATE_TRANSITION")
        val rows = jdbcTemplate.queryForList("select target_type, target_id, result from product_review_history where target_type = 'CHANGE_REQUEST'")
        assertEquals(1, rows.size)
        assertEquals(id, (rows[0]["target_id"] as Number).toLong())
        assertEquals("APPROVED", rows[0]["result"])
    }

    @Test
    fun 판매자는_자기_변경_요청을_조회하고_상세에서_최근_요청과_반려_사유를_본다() {
        val f = onSaleFixture()
        val id = f.submit(f.sameContentRequest(name = "새 티셔츠", originalPrice = 16000))

        val mine = json(get("/api/v1/seller/products/${f.productId}/change-requests/$id", f.token).andExpect { status { isOk() } }).get("data")
        assertEquals(id, mine.get("changeRequestId").asLong())
        assertEquals("PENDING", mine.get("status").asString())
        assertEquals(16000, mine.get("originalPrice").asLong())
        assertEquals("새 티셔츠", mine.get("content").get("name").asString())
        assertTrue(mine.get("requestedAt").asString().isNotBlank())
        assertEquals(id, sellerProductDetail(f.token, f.productId).get("latestChangeRequest").get("changeRequestId").asLong())

        val otherToken = newUserToken()
        approvedSeller(otherToken)
        get("/api/v1/seller/products/${f.productId}/change-requests/$id", otherToken).expectCode(404, "CHANGE_REQUEST_NOT_FOUND")

        post("/api/v1/admin/change-requests/$id/reject", adminToken(), mapOf("reason" to "이미지가 흐립니다")).andExpect { status { isOk() } }
        val latest = sellerProductDetail(f.token, f.productId).get("latestChangeRequest")
        assertEquals("REJECTED", latest.get("status").asString())
        assertEquals("이미지가 흐립니다", latest.get("rejectReason").asString())
        assertTrue(latest.get("requestedAt").asString().isNotBlank())
    }

    @Test
    fun 두_번째_요청은_409() {
        val f = onSaleFixture()
        f.submit()

        post("/api/v1/seller/products/${f.productId}/change-requests", f.token, f.sameContentRequest(name = "또 다른 이름"))
            .expectCode(409, "CHANGE_REQUEST_ALREADY_PENDING")
    }

    @Test
    fun 다른_판매자의_요청_철회는_404() {
        val f = onSaleFixture()
        val id = f.submit()
        val otherToken = newUserToken()
        approvedSeller(otherToken)

        delete("/api/v1/seller/products/${f.productId}/change-requests/$id", otherToken).expectCode(404, "CHANGE_REQUEST_NOT_FOUND")

        assertEquals(id, sellerProductDetail(f.token, f.productId).get("pendingChangeRequestId").asLong())
        delete("/api/v1/seller/products/${f.productId}/change-requests/$id", f.token).andExpect { status { isNoContent() } }
        assertTrue(sellerProductDetail(f.token, f.productId).get("pendingChangeRequestId").isNull)
        delete("/api/v1/seller/products/${f.productId}/change-requests/$id", f.token).expectCode(409, "CHANGE_REQUEST_INVALID_STATE_TRANSITION")
        f.submit()
    }

    @Test
    fun 반려하면_상품은_그대로고_심사_대기_상품과_종료된_상품은_막힌다() {
        val f = onSaleFixture()
        val id = f.submit(f.blackRedRequest())
        post("/api/v1/admin/change-requests/$id/reject", adminToken(), mapOf("reason" to "옵션 구성 오류")).andExpect { status { isOk() } }

        val detail = sellerProductDetail(f.token, f.productId)
        assertEquals("티셔츠", detail.get("name").asString())
        assertEquals(4, detail.get("skus").size())
        assertTrue(detail.get("pendingChangeRequestId").isNull)
        val request = json(get("/api/v1/admin/change-requests/$id", adminToken())).get("data")
        assertEquals("REJECTED", request.get("status").asString())
        assertEquals("옵션 구성 오류", request.get("rejectReason").asString())

        // 새 정가가 판매가(8000)보다 작으면 400.
        post("/api/v1/seller/products/${f.productId}/change-requests", f.token, f.sameContentRequest(name = "x", originalPrice = 7000))
            .expectCode(400, "PRODUCT_INVALID_PRICE")
        // 심사 대기 상품은 변경 요청을 낼 수 없다.
        val pendingProduct = registerProduct(f.token, approvedBrand(f.token, name = "두번째브랜드"), f.categoryId, f.fileIds)
        post("/api/v1/seller/products/$pendingProduct/change-requests", f.token, f.sameContentRequest(name = "x"))
            .expectCode(409, "PRODUCT_INVALID_STATE_TRANSITION")
        // 대기 중인 요청이 있는 상품을 종료하면 승인할 수 없고 요청은 PENDING 으로 남는다.
        val second = f.submit()
        post("/api/v1/seller/products/${f.productId}/end", f.token).andExpect { status { isOk() } }
        post("/api/v1/admin/change-requests/$second/approve", adminToken()).expectCode(409, "PRODUCT_INVALID_STATE_TRANSITION")
        assertEquals("PENDING", json(get("/api/v1/admin/change-requests/$second", adminToken())).get("data").get("status").asString())
        assertEquals("ENDED", sellerProductDetail(f.token, f.productId).get("status").asString())
        // 판매자 토큰으로 관리자 API 는 403.
        post("/api/v1/admin/change-requests/$second/approve", f.token).andExpect { status { isForbidden() } }
    }
}
