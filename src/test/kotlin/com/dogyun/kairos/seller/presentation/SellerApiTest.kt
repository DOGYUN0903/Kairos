package com.dogyun.kairos.seller.presentation

import com.dogyun.kairos.CommerceTestSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.springframework.test.web.servlet.ResultActionsDsl

class SellerApiTest : CommerceTestSupport() {

    private fun body(businessNumber: String = "123-45-67890") = mapOf(
        "companyName" to "키로스상사",
        "businessNumber" to businessNumber,
        "representativeName" to "홍길동",
        "phone" to "010-1234-5678",
        "bank" to "국민",
        "accountNumber" to "123456-01-123456",
        "holder" to "홍길동",
    )

    private fun ResultActionsDsl.expectCode(httpStatus: Int, code: String): ResultActionsDsl =
        andExpect { status { isEqualTo(httpStatus) } }.andExpect { jsonPath("$.code") { value(code) } }

    @Test
    fun 입점_신청하면_201과_내_정보가_PENDING이다() {
        val token = newUserToken()

        val created = post("/api/v1/seller/application", token, body()).andExpect { status { isCreated() } }
        val sellerId = json(created).get("data").get("sellerId").asLong()

        val me = json(get("/api/v1/seller/me", token).andExpect { status { isOk() } }).get("data")
        assertEquals(sellerId, me.get("sellerId").asLong())
        assertEquals("PENDING", me.get("status").asString())
        assertEquals(0, me.get("commissionRateBp").asInt())
        assertEquals("123-45-67890", me.get("businessNumber").asString())
        assertEquals("국민", me.get("bank").asString())
        assertNotNull(me.get("memberId"))
    }

    @Test
    fun 비로그인_신청은_401() {
        post("/api/v1/seller/application", null, body()).expectCode(401, "UNAUTHORIZED")
    }

    @Test
    fun 두_번_신청하면_409_SELLER_ALREADY_EXISTS() {
        val token = newUserToken()
        post("/api/v1/seller/application", token, body()).andExpect { status { isCreated() } }

        post("/api/v1/seller/application", token, body("111-22-33333"))
            .expectCode(409, "SELLER_ALREADY_EXISTS")
    }

    @Test
    fun 같은_사업자등록번호는_409_SELLER_BUSINESS_NUMBER_DUPLICATED() {
        post("/api/v1/seller/application", newUserToken(), body()).andExpect { status { isCreated() } }

        post("/api/v1/seller/application", newUserToken(), body())
            .expectCode(409, "SELLER_BUSINESS_NUMBER_DUPLICATED")
    }

    @Test
    fun 판매자가_아닌_회원의_내_정보는_404_SELLER_NOT_FOUND() {
        get("/api/v1/seller/me", newUserToken()).expectCode(404, "SELLER_NOT_FOUND")
    }

    @Test
    fun 사업자등록번호_형식_오류는_400() {
        post("/api/v1/seller/application", newUserToken(), body("1234567890"))
            .expectCode(400, "INVALID_REQUEST")
    }

    @Test
    fun PENDING_신청은_수정할_수_있다() {
        val token = newUserToken()
        post("/api/v1/seller/application", token, body()).andExpect { status { isCreated() } }

        put("/api/v1/seller/application", token, body("999-88-77777") + ("companyName" to "새상사"))
            .andExpect { status { isOk() } }

        val me = json(get("/api/v1/seller/me", token)).get("data")
        assertEquals("새상사", me.get("companyName").asString())
        assertEquals("999-88-77777", me.get("businessNumber").asString())
    }

    @Test
    fun 판매자가_아닌_회원의_신청_수정은_404() {
        put("/api/v1/seller/application", newUserToken(), body())
            .expectCode(404, "SELLER_NOT_FOUND")
    }

    @Test
    fun 승인된_판매자의_신청_수정은_409_INVALID_STATE_TRANSITION() {
        val token = newUserToken()
        post("/api/v1/seller/application", token, body()).andExpect { status { isCreated() } }
        jdbcTemplate.update("update seller set seller_status = 'APPROVED'")

        put("/api/v1/seller/application", token, body())
            .expectCode(409, "SELLER_INVALID_STATE_TRANSITION")
    }
}
