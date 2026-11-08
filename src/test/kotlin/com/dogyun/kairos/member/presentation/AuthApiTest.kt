package com.dogyun.kairos.member.presentation

import com.dogyun.kairos.IntegrationTestSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthApiTest : IntegrationTestSupport() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun newEmail() = "user-${UUID.randomUUID()}@kairos.com"

    private fun body(vararg pairs: Pair<String, Any>) = objectMapper.writeValueAsString(mapOf(*pairs))

    private fun signUp(email: String, password: String = "password1") = mockMvc.post("/api/v1/auth/signup") {
        contentType = MediaType.APPLICATION_JSON
        content = body("email" to email, "password" to password, "name" to "홍길동")
    }

    private fun login(email: String, password: String = "password1") = mockMvc.post("/api/v1/auth/login") {
        contentType = MediaType.APPLICATION_JSON
        content = body("email" to email, "password" to password)
    }

    private fun reissue(refreshToken: String) = mockMvc.post("/api/v1/auth/reissue") {
        contentType = MediaType.APPLICATION_JSON
        content = body("refreshToken" to refreshToken)
    }

    private fun MvcResult.json(path: String): String {
        val node = objectMapper.readTree(response.contentAsString)
        return path.split(".").fold(node) { n, key -> n.get(key) }.asString()
    }

    private fun loginTokens(email: String): Pair<String, String> {
        signUp(email)
        val result = login(email).andReturn()
        return result.json("data.accessToken") to result.json("data.refreshToken")
    }

    @Test
    fun 회원가입_후_로그인하면_토큰을_받는다() {
        val email = newEmail()

        signUp(email).andExpect {
            status { isCreated() }
            jsonPath("$.data.memberId") { isNumber() }
            jsonPath("$.message") { isNotEmpty() }
            jsonPath("$.timestamp") { isNotEmpty() }
            jsonPath("$.data.password") { doesNotExist() }
        }
        login(email).andExpect {
            status { isOk() }
            jsonPath("$.data.accessToken") { isNotEmpty() }
            jsonPath("$.data.refreshToken") { isNotEmpty() }
        }
    }

    @Test
    fun 비밀번호는_해시로_저장된다() {
        val email = newEmail()
        signUp(email)

        val stored = jdbcTemplate.queryForObject("select password from member where email = ?", String::class.java, email)

        assert(stored != "password1" && stored!!.startsWith("\$2"))
    }

    @Test
    fun 중복_이메일은_409() {
        val email = newEmail()
        signUp(email)

        signUp(email).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("MEMBER_EMAIL_DUPLICATED") }
        }
    }

    @Test
    fun 잘못된_요청은_400() {
        signUp("not-an-email").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
        signUp(newEmail(), "short").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun 틀린_비밀번호는_401() {
        val email = newEmail()
        signUp(email)

        login(email, "wrong-password").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_INVALID_CREDENTIALS") }
        }
    }

    @Test
    fun 재발급하면_이전_리프레시_토큰은_무효다() {
        val (_, oldRefresh) = loginTokens(newEmail())

        val reissued = reissue(oldRefresh).andExpect {
            status { isOk() }
            jsonPath("$.data.accessToken") { isNotEmpty() }
        }.andReturn()

        reissue(oldRefresh).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_INVALID_TOKEN") }
        }
        reissue(reissued.json("data.refreshToken")).andExpect { status { isOk() } }
    }

    @Test
    fun 로그아웃_후_재발급은_401() {
        val (access, refresh) = loginTokens(newEmail())

        mockMvc.post("/api/v1/auth/logout") { header("Authorization", "Bearer $access") }
            .andExpect { status { isNoContent() } }

        reissue(refresh).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_INVALID_TOKEN") }
        }
    }

    @Test
    fun 토큰_없이_보호된_API를_호출하면_401_에러_응답이다() {
        mockMvc.post("/api/v1/auth/logout").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
            jsonPath("$.timestamp") { isNotEmpty() }
        }
        mockMvc.get("/api/v1/orders/1").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun 리프레시_토큰으로는_보호된_API를_호출할_수_없다() {
        val (_, refresh) = loginTokens(newEmail())

        mockMvc.post("/api/v1/auth/logout") { header("Authorization", "Bearer $refresh") }
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun 일반_회원은_관리자_API에_403() {
        val (access, _) = loginTokens(newEmail())

        mockMvc.post("/api/v1/admin/deals") {
            header("Authorization", "Bearer $access")
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun 시작시_관리자_계정이_생성되고_로그인할_수_있다() {
        login("admin@kairos.test", "test-admin-password").andExpect {
            status { isOk() }
            jsonPath("$.data.accessToken") { isNotEmpty() }
        }
        val role = jdbcTemplate.queryForObject("select role from member where email = 'admin@kairos.test'", String::class.java)
        assertEquals("ADMIN", role)
    }

    @Test
    fun 회원가입시_created_by는_0이다() {
        val email = newEmail()
        signUp(email)

        val createdBy = jdbcTemplate.queryForObject("select created_by from member where email = ?", Long::class.java, email)

        assertEquals(0L, createdBy)
    }
}
