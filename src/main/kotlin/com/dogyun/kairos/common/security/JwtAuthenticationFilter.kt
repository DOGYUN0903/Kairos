package com.dogyun.kairos.common.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Bearer 액세스 토큰이 유효하면 인증 정보를 채운다. 유효하지 않으면 아무것도 하지 않고,
 * 보호된 경로에서는 인증 진입점이 401 을 응답한다. 서블릿 필터로 중복 등록되지 않도록 빈이 아니다.
 */
class JwtAuthenticationFilter(private val jwtProvider: JwtProvider) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            jwtProvider.parseAccessToken(header.substring(BEARER_PREFIX.length).trim())?.let { claims ->
                SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                    LoginMember(claims.memberId, claims.role),
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_${claims.role.name}")),
                )
            }
        }
        filterChain.doFilter(request, response)
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
