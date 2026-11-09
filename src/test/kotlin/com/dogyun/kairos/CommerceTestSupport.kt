package com.dogyun.kairos

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import kotlin.test.BeforeTest

/** 판매자·카탈로그 API 통합 테스트 공통 도구. 이후 Task 가 cleanUp 대상 테이블을 늘린다. */
abstract class CommerceTestSupport : IntegrationTestSupport() {

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @BeforeTest
    fun cleanUp() {
        jdbcTemplate.update("delete from file")
        jdbcTemplate.update("delete from seller_review_history")
        jdbcTemplate.update("delete from seller")
    }

    protected fun json(result: MvcResult): JsonNode = objectMapper.readTree(result.response.contentAsString)

    protected fun json(result: ResultActionsDsl): JsonNode = json(result.andReturn())

    private fun login(email: String, password: String): String {
        val result = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))
        }.andReturn()
        return json(result).get("data").get("accessToken").asString()
    }

    protected fun adminToken(): String = login("admin@kairos.test", "test-admin-password")

    /** 랜덤 이메일로 가입하고 로그인한 일반 회원의 액세스 토큰. */
    protected fun newUserToken(): String {
        val email = "user-${UUID.randomUUID()}@kairos.com"
        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to email, "password" to "password1", "name" to "홍길동"))
        }
        return login(email, "password1")
    }

    protected fun post(path: String, token: String? = null, body: Any? = null): ResultActionsDsl =
        mockMvc.post(path) { prepare(token, body) }

    protected fun put(path: String, token: String? = null, body: Any? = null): ResultActionsDsl =
        mockMvc.put(path) { prepare(token, body) }

    protected fun patch(path: String, token: String? = null, body: Any? = null): ResultActionsDsl =
        mockMvc.patch(path) { prepare(token, body) }

    protected fun get(path: String, token: String? = null): ResultActionsDsl =
        mockMvc.get(path) { prepare(token, null) }

    protected fun delete(path: String, token: String? = null): ResultActionsDsl =
        mockMvc.delete(path) { prepare(token, null) }

    /** 판매자 신청 → 관리자 승인(수수료율 1000bp)까지 마치고 sellerId 를 돌려준다. */
    protected fun approvedSeller(token: String, businessNumber: String = randomBusinessNumber()): Long {
        val created = post(
            "/api/v1/seller/application", token,
            mapOf(
                "companyName" to "키로스상사",
                "businessNumber" to businessNumber,
                "representativeName" to "홍길동",
                "phone" to "010-1234-5678",
                "bank" to "국민",
                "accountNumber" to "123456-01-123456",
                "holder" to "홍길동",
            ),
        ).andExpect { status { isCreated() } }
        val sellerId = json(created).get("data").get("sellerId").asLong()
        post("/api/v1/admin/sellers/$sellerId/approve", adminToken(), mapOf("commissionRateBp" to 1000))
            .andExpect { status { isOk() } }
        return sellerId
    }

    /** 1x1 PNG 를 업로드하고 fileId 를 돌려준다. */
    protected fun uploadImage(token: String): Long {
        val result = mockMvc.multipart("/api/v1/files/images") {
            file(MockMultipartFile("file", "dot.png", "image/png", PNG_1X1))
            header("Authorization", "Bearer $token")
        }.andExpect { status { isCreated() } }
        return json(result).get("data").get("fileId").asLong()
    }

    protected fun suspendSeller(sellerId: Long) {
        post("/api/v1/admin/sellers/$sellerId/suspend", adminToken(), mapOf("reason" to "약관 위반"))
            .andExpect { status { isOk() } }
    }

    protected fun unsuspendSeller(sellerId: Long) {
        post("/api/v1/admin/sellers/$sellerId/unsuspend", adminToken())
            .andExpect { status { isOk() } }
    }

    companion object {
        val PNG_1X1: ByteArray = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgYGD4DwABBAEAwS2OUAAAAABJRU5ErkJggg==",
        )
    }

    private fun randomBusinessNumber(): String {
        val n = UUID.randomUUID().mostSignificantBits.let { Math.floorMod(it, 1_000_000_000L) }
        return "%03d-%02d-%05d".format(n / 10_000_000, (n / 100_000) % 100, n % 100_000)
    }

    private fun MockHttpServletRequestDsl.prepare(token: String?, body: Any?) {
        token?.let { header("Authorization", "Bearer $it") }
        if (body != null) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
        }
    }
}
