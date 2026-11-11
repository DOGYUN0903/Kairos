package com.dogyun.kairos.member.presentation

import com.dogyun.kairos.IntegrationTestSupport
import jakarta.servlet.http.Cookie
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        mockMvc.get("/api/v1/auth/me").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
            jsonPath("$.timestamp") { isNotEmpty() }
        }
        mockMvc.get("/api/v1/orders/1").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun 리프레시_토큰으로는_보호된_API를_호출할_수_없다() {
        val (_, refresh) = loginTokens(newEmail())

        mockMvc.get("/api/v1/auth/me") { header("Authorization", "Bearer $refresh") }
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

    @Test
    fun 잘못된_Content_Type으로_로그인하면_415_ErrorResponse() {
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.TEXT_PLAIN
            content = "email=a"
        }.andExpect {
            status { isUnsupportedMediaType() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
            jsonPath("$.timestamp") { exists() }
        }
    }

    private fun cookieReissue(cookie: String?, requestedWith: String? = "kairos-web") =
        mockMvc.post("/api/v1/auth/reissue") {
            cookie?.let { cookie(Cookie("kairos_refresh", it)) }
            requestedWith?.let { header("X-Requested-With", it) }
        }

    private fun MvcResult.setCookie(): String = requireNotNull(response.getHeader("Set-Cookie"))

    @Test
    fun 로그인_응답은_kairos_refresh_쿠키를_HttpOnly_SameSite_Strict_Path_auth_MaxAge_14일로_내려준다() {
        val email = newEmail()
        signUp(email)
        val result = login(email).andExpect { status { isOk() } }.andReturn()

        val header = result.setCookie()
        val refresh = result.json("data.refreshToken")
        assertTrue(header.startsWith("kairos_refresh=$refresh"), header)
        listOf("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth", "Max-Age=1209600").forEach {
            assertTrue(header.contains(it), "$it 없음: $header")
        }
        assertFalse(header.contains("Secure"), header)
    }

    @Test
    fun 본문_없이_쿠키와_X_Requested_With로_reissue하면_새_토큰과_새_쿠키를_받고_옛_쿠키는_401() {
        val (_, oldRefresh) = loginTokens(newEmail())

        val result = cookieReissue(oldRefresh).andExpect {
            status { isOk() }
            jsonPath("$.data.accessToken") { isNotEmpty() }
        }.andReturn()
        val newRefresh = result.json("data.refreshToken")
        assertTrue(result.setCookie().startsWith("kairos_refresh=$newRefresh"))

        cookieReissue(oldRefresh).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_INVALID_TOKEN") }
        }
        cookieReissue(newRefresh).andExpect { status { isOk() } }
    }

    @Test
    fun 헤더_없는_쿠키_reissue는_400_INVALID_REQUEST() {
        val (_, refresh) = loginTokens(newEmail())

        cookieReissue(refresh, requestedWith = null).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
        cookieReissue(refresh, requestedWith = "XMLHttpRequest").andExpect { status { isBadRequest() } }
        cookieReissue(null).andExpect { status { isBadRequest() } }
    }

    @Test
    fun 본문_refreshToken은_헤더_없이도_계속_동작한다() {
        val (_, refresh) = loginTokens(newEmail())

        val result = reissue(refresh).andExpect {
            status { isOk() }
            jsonPath("$.data.refreshToken") { isNotEmpty() }
        }.andReturn()
        assertTrue(result.setCookie().startsWith("kairos_refresh=${result.json("data.refreshToken")}"))
    }

    @Test
    fun 액세스_토큰_없이_쿠키와_헤더만으로_logout하면_쿠키가_만료되고_이후_쿠키_reissue는_401() {
        val (_, refresh) = loginTokens(newEmail())

        val result = mockMvc.post("/api/v1/auth/logout") {
            cookie(Cookie("kairos_refresh", refresh))
            header("X-Requested-With", "kairos-web")
        }.andExpect { status { isNoContent() } }.andReturn()
        assertTrue(result.setCookie().contains("Max-Age=0"), result.setCookie())

        cookieReissue(refresh).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_INVALID_TOKEN") }
        }
    }

    @Test
    fun 회전된_옛_쿠키로_logout해도_현재_리프레시_토큰은_살아있다() {
        val (_, oldRefresh) = loginTokens(newEmail())
        val current = reissue(oldRefresh).andReturn().json("data.refreshToken")

        val result = mockMvc.post("/api/v1/auth/logout") {
            cookie(Cookie("kairos_refresh", oldRefresh))
            header("X-Requested-With", "kairos-web")
        }.andExpect { status { isNoContent() } }.andReturn()
        assertTrue(result.setCookie().contains("Max-Age=0"))

        reissue(current).andExpect { status { isOk() } }
    }

    @Test
    fun 헤더_없는_쿠키_logout은_토큰을_지우지_않는다() {
        val (_, refresh) = loginTokens(newEmail())

        mockMvc.post("/api/v1/auth/logout") { cookie(Cookie("kairos_refresh", refresh)) }
            .andExpect { status { isNoContent() } }

        reissue(refresh).andExpect { status { isOk() } }
    }

    @Test
    fun 액세스_토큰으로_logout해도_쿠키가_만료된다() {
        val (access, refresh) = loginTokens(newEmail())

        val result = mockMvc.post("/api/v1/auth/logout") { header("Authorization", "Bearer $access") }
            .andExpect { status { isNoContent() } }.andReturn()
        assertTrue(result.setCookie().contains("Max-Age=0"))
        reissue(refresh).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun 토큰도_쿠키도_없는_logout은_204() {
        val result = mockMvc.post("/api/v1/auth/logout").andExpect { status { isNoContent() } }.andReturn()
        assertTrue(result.setCookie().contains("Max-Age=0"))
    }

    @Test
    fun auth_me는_memberId_email_name_role을_준다() {
        val email = newEmail()
        val (access, _) = loginTokens(email)

        mockMvc.get("/api/v1/auth/me") { header("Authorization", "Bearer $access") }.andExpect {
            status { isOk() }
            jsonPath("$.data.memberId") { isNumber() }
            jsonPath("$.data.email") { value(email) }
            jsonPath("$.data.name") { value("홍길동") }
            jsonPath("$.data.role") { value("USER") }
        }
    }

    @Test
    fun 비로그인_auth_me는_401() {
        mockMvc.get("/api/v1/auth/me").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }
}
