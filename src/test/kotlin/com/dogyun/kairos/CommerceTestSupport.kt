package com.dogyun.kairos

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
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

    private fun MockHttpServletRequestDsl.prepare(token: String?, body: Any?) {
        token?.let { header("Authorization", "Bearer $it") }
        if (body != null) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
        }
    }
}
