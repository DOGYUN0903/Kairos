package com.dogyun.kairos

import tools.jackson.databind.JsonNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminQueueSortApiTest : CommerceTestSupport() {

    private fun ids(path: String, idField: String): List<Long> {
        val content = json(get(path, adminToken()).andExpect { status { isOk() } }).get("data").get("content")
        return (0 until content.size()).map { content.get(it).get(idField).asLong() }
    }

    /** 대기 중인 판매자·브랜드·상품·변경 요청을 각각 둘씩 만든다. */
    private fun twoOfEach() {
        // 판매자: 신청만 하고 승인하지 않는다.
        repeat(2) {
            val token = newUserToken()
            appliedSeller(token, companyName = "대기상사")
            Thread.sleep(2)
        }
        val token = newUserToken()
        approvedSeller(token)
        repeat(2) { post("/api/v1/seller/brands", token, mapOf("name" to "대기브랜드-$it-${System.nanoTime()}")).andExpect { status { isCreated() } } }
        val brandId = approvedBrand(token)
        val categoryId = leafCategory()
        val fileIds = listOf(uploadImage(token))
        repeat(2) { registerProduct(token, brandId, categoryId, fileIds) }
        repeat(2) {
            val productId = onSaleProduct(token, brandId, categoryId, fileIds)
            post(
                "/api/v1/seller/products/$productId/change-requests", token,
                mapOf("content" to productContent(categoryId, fileIds, name = "새 티셔츠"), "originalPrice" to 10000),
            ).andExpect { status { isCreated() } }
        }
    }

    @Test
    fun 관리자_큐_네_개는_sort_OLDEST면_id_오름차순이고_기본은_내림차순() {
        twoOfEach()
        val queues = listOf(
            "/api/v1/admin/sellers?status=PENDING" to "sellerId",
            "/api/v1/admin/brands?status=PENDING" to "brandId",
            "/api/v1/admin/products?status=PENDING_REVIEW" to "productId",
            "/api/v1/admin/change-requests?status=PENDING" to "changeRequestId",
        )
        for ((path, field) in queues) {
            val oldest = ids("$path&sort=OLDEST", field)
            val default = ids(path, field)
            val latest = ids("$path&sort=LATEST", field)
            assertTrue(oldest.size >= 2, "$path 행 수")
            assertEquals(oldest.sorted(), oldest, "$path OLDEST 는 오름차순")
            assertEquals(oldest.sortedDescending(), default, "$path 기본은 내림차순")
            assertEquals(default, latest, "$path LATEST 는 기본과 같다")
            assertTrue(oldest.first() < oldest[1])
        }
    }

    @Test
    fun sort_값이_잘못되면_400() {
        for (path in listOf("sellers", "brands", "products", "change-requests")) {
            get("/api/v1/admin/$path?sort=NEWEST", adminToken())
                .andExpect { status { isBadRequest() } }
        }
    }
}
