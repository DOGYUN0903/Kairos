package com.dogyun.kairos.common.presentation

import io.micrometer.tracing.Tracer
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 현재 요청의 traceId를 응답 헤더 `X-Trace-Id`로 돌려준다.
 *
 * 관측 필터(HIGHEST_PRECEDENCE + 1)가 span을 연 직후, Spring Security 필터보다 먼저 실행되므로
 * 인증 실패(401) 응답에도 헤더가 붙는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class TraceIdResponseFilter(private val tracer: Tracer) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        tracer.currentSpan()?.context()?.traceId()?.let { response.setHeader(HEADER, it) }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val HEADER = "X-Trace-Id"
    }
}
