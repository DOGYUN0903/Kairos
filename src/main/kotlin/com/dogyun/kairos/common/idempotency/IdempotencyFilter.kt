package com.dogyun.kairos.common.idempotency

import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.presentation.ErrorResponse
import com.dogyun.kairos.common.security.LoginMember
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper
import tools.jackson.databind.ObjectMapper
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDateTime

/**
 * 대상 경로의 인증된 POST 요청에 Idempotency-Key 를 강제한다. 보안 필터 체인 뒤에서 동작하므로
 * 인증 정보는 SecurityContextHolder 에서 읽는다.
 */
class IdempotencyFilter(
    private val store: IdempotencyStore,
    private val properties: IdempotencyProperties,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) : OncePerRequestFilter() {

    private val pathMatcher = AntPathMatcher()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method != "POST" || properties.paths.none { pathMatcher.match(it, request.requestURI) }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val memberId = (SecurityContextHolder.getContext().authentication?.principal as? LoginMember)?.id
        if (memberId == null) {
            filterChain.doFilter(request, response)
            return
        }
        val key = request.getHeader(HEADER)
        if (key == null || key.length !in 1..MAX_KEY_LENGTH) {
            writeError(response, ErrorCode.IDEMPOTENCY_KEY_REQUIRED)
            return
        }

        val body = request.inputStream.readAllBytes()
        val now = LocalDateTime.now(clock)
        when (val result = store.begin(memberId, key, hash(request, body), now, now.plus(properties.ttl), properties.inProgressTimeout)) {
            is BeginResult.Completed -> replay(response, result)
            BeginResult.InProgress -> writeError(response, ErrorCode.IDEMPOTENCY_IN_PROGRESS)
            BeginResult.HashMismatch -> writeError(response, ErrorCode.IDEMPOTENCY_KEY_REUSED)
            BeginResult.Started -> process(memberId, key, CachedBodyRequest(request, body), response, filterChain)
        }
    }

    private fun process(
        memberId: Long,
        key: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val wrapper = ContentCachingResponseWrapper(response)
        var completed = false
        try {
            filterChain.doFilter(request, wrapper)
            if (wrapper.status < 500) {
                store.complete(memberId, key, wrapper.status, String(wrapper.contentAsByteArray, Charsets.UTF_8))
                completed = true
            }
        } finally {
            // 5xx 나 예외는 저장하지 않고 키를 풀어 재시도할 수 있게 한다.
            try {
                if (!completed) store.release(memberId, key)
            } finally {
                wrapper.copyBodyToResponse()
            }
        }
    }

    private fun replay(response: HttpServletResponse, result: BeginResult.Completed) {
        response.status = result.status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(result.body)
    }

    private fun writeError(response: HttpServletResponse, code: ErrorCode) {
        response.status = code.httpStatus
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(objectMapper.writeValueAsString(ErrorResponse(code.name, code.message)))
    }

    private fun hash(request: HttpServletRequest, body: ByteArray): String =
        MessageDigest.getInstance("SHA-256").run {
            update("${request.method}\n${request.requestURI}\n".toByteArray(Charsets.UTF_8))
            update(body)
            digest().joinToString("") { "%02x".format(it) }
        }

    /** 이미 읽은 본문을 다시 읽을 수 있게 하는 요청 래퍼. */
    private class CachedBodyRequest(request: HttpServletRequest, private val body: ByteArray) :
        HttpServletRequestWrapper(request) {

        override fun getInputStream(): ServletInputStream {
            val source = ByteArrayInputStream(body)
            return object : ServletInputStream() {
                override fun read(): Int = source.read()
                override fun isFinished(): Boolean = source.available() == 0
                override fun isReady(): Boolean = true
                override fun setReadListener(listener: ReadListener?) = throw UnsupportedOperationException()
            }
        }

        override fun getReader(): BufferedReader =
            BufferedReader(InputStreamReader(inputStream, characterEncoding ?: Charsets.UTF_8.name()))
    }

    private companion object {
        const val HEADER = "Idempotency-Key"
        const val MAX_KEY_LENGTH = 100
    }
}
