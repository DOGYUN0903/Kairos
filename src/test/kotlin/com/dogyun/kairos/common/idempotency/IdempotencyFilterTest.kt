package com.dogyun.kairos.common.idempotency

import com.dogyun.kairos.common.security.LoginMember
import com.dogyun.kairos.common.security.Role
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IdempotencyFilterTest {

    private class FakeIdempotencyStore : IdempotencyStore {
        data class Row(val hash: String, var completed: Boolean = false, var status: Int = 0, var body: String = "")

        val rows = mutableMapOf<Pair<Long, String>, Row>()
        val released = mutableListOf<Pair<Long, String>>()

        override fun begin(
            memberId: Long, key: String, requestHash: String, now: LocalDateTime,
            expiresAt: LocalDateTime, inProgressTimeout: Duration,
        ): BeginResult {
            val row = rows[memberId to key]
            if (row == null) {
                rows[memberId to key] = Row(requestHash)
                return BeginResult.Started
            }
            if (row.hash != requestHash) return BeginResult.HashMismatch
            return if (row.completed) BeginResult.Completed(row.status, row.body) else BeginResult.InProgress
        }

        override fun complete(memberId: Long, key: String, status: Int, body: String) {
            rows.getValue(memberId to key).apply { completed = true; this.status = status; this.body = body }
        }

        override fun release(memberId: Long, key: String) {
            rows.remove(memberId to key)
            released += memberId to key
        }

        override fun deleteExpired(now: LocalDateTime): Int = 0
    }

    private val store = FakeIdempotencyStore()
    private val clock = Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val filter = IdempotencyFilter(store, IdempotencyProperties(), ObjectMapper(), clock)

    @BeforeEach
    fun login() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(LoginMember(7L, Role.USER), null, emptyList())
    }

    @AfterEach
    fun logout() = SecurityContextHolder.clearContext()

    private fun post(path: String = "/api/v1/orders", body: String = """{"a":1}""", key: String? = "key-1") =
        MockHttpServletRequest("POST", path).apply {
            requestURI = path
            contentType = "application/json"
            setContent(body.toByteArray())
            key?.let { addHeader("Idempotency-Key", it) }
        }

    /** 체인이 요청 본문을 읽은 뒤 주어진 상태·본문을 쓴다. 호출 횟수와 읽은 본문을 기록한다. */
    private class RecordingChain(private val status: Int = 201, private val responseBody: String = """{"message":"created"}""") :
        FilterChain {
        var calls = 0
        var readBody: String? = null

        override fun doFilter(request: jakarta.servlet.ServletRequest, response: jakarta.servlet.ServletResponse) {
            calls++
            readBody = String(request.inputStream.readAllBytes())
            (response as jakarta.servlet.http.HttpServletResponse).status = status
            response.contentType = "application/json"
            response.writer.write(responseBody)
        }
    }

    @Test
    fun 대상_경로_POST에_헤더가_없으면_400() {
        val response = MockHttpServletResponse()
        val chain = RecordingChain()

        filter.doFilter(post(key = null), response, chain)

        assertEquals(400, response.status)
        assertTrue(response.contentAsString.contains("IDEMPOTENCY_KEY_REQUIRED"))
        assertEquals(0, chain.calls)
    }

    @Test
    fun 키가_100자를_넘으면_400() {
        val response = MockHttpServletResponse()

        filter.doFilter(post(key = "k".repeat(101)), response, RecordingChain())

        assertEquals(400, response.status)
    }

    @Test
    fun GET이나_대상_외_경로는_그대로_통과() {
        val get = MockHttpServletRequest("GET", "/api/v1/orders").apply { requestURI = "/api/v1/orders" }
        val getChain = RecordingChain(200, "{}")
        filter.doFilter(get, MockHttpServletResponse(), getChain)
        val otherChain = RecordingChain(200, "{}")
        filter.doFilter(post(path = "/api/v1/seller/brands", key = null), MockHttpServletResponse(), otherChain)

        assertEquals(1, getChain.calls)
        assertEquals(1, otherChain.calls)
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun 와일드카드_취소_경로도_대상이다() {
        val response = MockHttpServletResponse()

        filter.doFilter(post(path = "/api/v1/orders/10/items/20/cancel", key = null), response, RecordingChain())

        assertEquals(400, response.status)
    }

    @Test
    fun 인증되지_않은_요청은_그대로_통과() {
        SecurityContextHolder.clearContext()
        val chain = RecordingChain()

        filter.doFilter(post(key = null), MockHttpServletResponse(), chain)

        assertEquals(1, chain.calls)
    }

    @Test
    fun 정상_응답은_저장되고_본문이_체인에_다시_읽힌다() {
        val response = MockHttpServletResponse()
        val chain = RecordingChain()

        filter.doFilter(post(), response, chain)

        assertEquals("""{"a":1}""", chain.readBody)
        assertEquals(201, response.status)
        assertEquals("""{"message":"created"}""", response.contentAsString)
        assertTrue(store.rows.getValue(7L to "key-1").completed)
    }

    @Test
    fun 같은_키_재요청은_체인을_타지_않고_저장된_상태코드와_본문을_돌려준다() {
        filter.doFilter(post(), MockHttpServletResponse(), RecordingChain())
        val response = MockHttpServletResponse()
        val chain = RecordingChain()

        filter.doFilter(post(), response, chain)

        assertEquals(0, chain.calls)
        assertEquals(201, response.status)
        assertEquals("""{"message":"created"}""", response.contentAsString)
    }

    @Test
    fun 처리_중_키는_409() {
        store.rows[7L to "key-1"] = FakeIdempotencyStore.Row(hashOf("POST", "/api/v1/orders", """{"a":1}"""))
        val response = MockHttpServletResponse()
        val chain = RecordingChain()

        filter.doFilter(post(), response, chain)

        assertEquals(409, response.status)
        assertTrue(response.contentAsString.contains("IDEMPOTENCY_IN_PROGRESS"))
        assertEquals(0, chain.calls)
    }

    @Test
    fun 본문이_다르면_422() {
        filter.doFilter(post(), MockHttpServletResponse(), RecordingChain())
        val response = MockHttpServletResponse()
        val chain = RecordingChain()

        filter.doFilter(post(body = """{"a":2}"""), response, chain)

        assertEquals(422, response.status)
        assertTrue(response.contentAsString.contains("IDEMPOTENCY_KEY_REUSED"))
        assertEquals(0, chain.calls)
    }

    @Test
    fun `5xx 응답은 저장하지 않고 release한다`() {
        val response = MockHttpServletResponse()

        filter.doFilter(post(), response, RecordingChain(500, """{"code":"INTERNAL_ERROR"}"""))

        assertEquals(listOf(7L to "key-1"), store.released)
        assertTrue(store.rows.isEmpty())
        assertEquals(500, response.status)
        assertEquals("""{"code":"INTERNAL_ERROR"}""", response.contentAsString)
    }

    @Test
    fun 체인이_예외를_던져도_release된다() {
        val chain = FilterChain { _, _ -> throw IllegalStateException("boom") }

        assertFailsWith<IllegalStateException> { filter.doFilter(post(), MockHttpServletResponse(), chain) }

        assertEquals(listOf(7L to "key-1"), store.released)
    }

    @Test
    fun 재전송과_정상_응답_모두_본문이_클라이언트에_쓰인다() {
        val first = MockHttpServletResponse()
        filter.doFilter(post(), first, MockFilterChain())
        // 체인이 아무것도 쓰지 않은 200 도 저장·재전송된다.
        assertEquals(200, first.status)
        assertNotNull(store.rows[7L to "key-1"])

        val second = MockHttpServletResponse()
        filter.doFilter(post(key = "key-2"), second, RecordingChain())
        val replay = MockHttpServletResponse()
        filter.doFilter(post(key = "key-2"), replay, RecordingChain())

        assertEquals(second.contentAsString, replay.contentAsString)
        assertEquals("""{"message":"created"}""", replay.contentAsString)
    }

    private fun hashOf(method: String, path: String, body: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest("$method\n$path\n$body".toByteArray())
            .joinToString("") { "%02x".format(it) }
}
