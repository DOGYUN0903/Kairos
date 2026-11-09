package com.dogyun.kairos.seller.presentation

import com.dogyun.kairos.CommerceTestSupport
import org.springframework.test.web.servlet.ResultActionsDsl
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminSellerApiTest : CommerceTestSupport() {

    private fun ResultActionsDsl.expectCode(httpStatus: Int, code: String): ResultActionsDsl =
        andExpect { status { isEqualTo(httpStatus) } }.andExpect { jsonPath("$.code") { value(code) } }

    private fun application(businessNumber: String = "123-45-67890") = mapOf(
        "companyName" to "키로스상사",
        "businessNumber" to businessNumber,
        "representativeName" to "홍길동",
        "phone" to "010-1234-5678",
        "bank" to "국민",
        "accountNumber" to "123456-01-123456",
        "holder" to "홍길동",
    )

    private fun apply(token: String): Long =
        json(post("/api/v1/seller/application", token, application()).andExpect { status { isCreated() } })
            .get("data").get("sellerId").asLong()

    @Test
    fun 관리자가_승인하면_판매자_내_정보가_APPROVED_수수료율_1000() {
        val token = newUserToken()

        approvedSeller(token)

        val me = json(get("/api/v1/seller/me", token)).get("data")
        assertEquals("APPROVED", me.get("status").asString())
        assertEquals(1000, me.get("commissionRateBp").asInt())
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from seller_review_history", Int::class.java))
    }

    @Test
    fun 상태_필터로_심사_목록을_조회한다() {
        approvedSeller(newUserToken(), "100-00-00001")
        apply(newUserToken())
        val admin = adminToken()

        val approved = json(get("/api/v1/admin/sellers?status=APPROVED", admin).andExpect { status { isOk() } }).get("data")
        val pending = json(get("/api/v1/admin/sellers?status=PENDING&page=0&size=10", admin)).get("data")

        assertEquals(1, approved.get("totalElements").asInt())
        assertEquals("APPROVED", approved.get("content").get(0).get("status").asString())
        assertEquals(1, pending.get("totalElements").asInt())
        assertEquals("PENDING", pending.get("content").get(0).get("status").asString())
    }

    @Test
    fun 일반_회원이_관리자_API를_부르면_403() {
        get("/api/v1/admin/sellers", newUserToken()).expectCode(403, "FORBIDDEN")
        post("/api/v1/admin/sellers/1/approve", newUserToken(), mapOf("commissionRateBp" to 1000))
            .expectCode(403, "FORBIDDEN")
    }

    @Test
    fun 승인된_판매자_재승인은_409_SELLER_INVALID_STATE_TRANSITION() {
        val sellerId = approvedSeller(newUserToken())

        post("/api/v1/admin/sellers/$sellerId/approve", adminToken(), mapOf("commissionRateBp" to 500))
            .expectCode(409, "SELLER_INVALID_STATE_TRANSITION")
    }

    @Test
    fun 정지_해제_후_내_정보가_APPROVED다() {
        val token = newUserToken()
        val sellerId = approvedSeller(token)

        suspendSeller(sellerId)
        val suspended = json(get("/api/v1/seller/me", token)).get("data")
        assertEquals("SUSPENDED", suspended.get("status").asString())
        assertEquals("약관 위반", suspended.get("suspendReason").asString())
        unsuspendSeller(sellerId)

        val me = json(get("/api/v1/seller/me", token)).get("data")
        assertEquals("APPROVED", me.get("status").asString())
        assertEquals(true, me.get("suspendReason").isNull)
    }

    @Test
    fun 없는_판매자_승인은_404_SELLER_NOT_FOUND() {
        post("/api/v1/admin/sellers/999999/approve", adminToken(), mapOf("commissionRateBp" to 1000))
            .expectCode(404, "SELLER_NOT_FOUND")
    }

    @Test
    fun 수수료율_범위와_빈_사유는_400() {
        val sellerId = apply(newUserToken())
        val admin = adminToken()

        post("/api/v1/admin/sellers/$sellerId/approve", admin, mapOf("commissionRateBp" to 10001))
            .expectCode(400, "INVALID_REQUEST")
        post("/api/v1/admin/sellers/$sellerId/reject", admin, mapOf("reason" to " "))
            .expectCode(400, "INVALID_REQUEST")
    }

    @Test
    fun 반려된_신청을_고치면_PENDING으로_돌아가고_반려_사유가_지워진다() {
        val token = newUserToken()
        val sellerId = apply(token)

        post("/api/v1/admin/sellers/$sellerId/reject", adminToken(), mapOf("reason" to "서류 미비"))
            .andExpect { status { isOk() } }
        val rejected = json(get("/api/v1/seller/me", token)).get("data")
        assertEquals("REJECTED", rejected.get("status").asString())
        assertEquals("서류 미비", rejected.get("rejectReason").asString())

        put("/api/v1/seller/application", token, application("999-88-77777") + ("companyName" to "새상사"))
            .andExpect { status { isOk() } }

        val me = json(get("/api/v1/seller/me", token)).get("data")
        assertEquals("PENDING", me.get("status").asString())
        assertEquals("새상사", me.get("companyName").asString())
        assertEquals(true, me.get("rejectReason").isNull)
    }
}
