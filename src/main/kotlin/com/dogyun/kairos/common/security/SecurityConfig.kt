package com.dogyun.kairos.common.security

import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.presentation.ErrorResponse
import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.context.support.WebApplicationContextUtils
import tools.jackson.databind.ObjectMapper

@Configuration
class SecurityConfig {

    /**
     * Actuator 엔드포인트용 체인. 관리 포트(8081)는 로컬/내부망 전용이라 JWT 없이 연다.
     * 관리 서버(네임스페이스 "management")로 들어온 요청에만 매칭되므로,
     * 서비스 포트(8080)의 요청은 아래 JWT 체인에서 그대로 인증 대상이 된다.
     */
    @Bean
    @Order(0)
    fun managementSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher(managementServerRequest())
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }

    @Bean
    @Order(1)
    fun securityFilterChain(
        http: HttpSecurity,
        jwtProvider: JwtProvider,
        objectMapper: ObjectMapper,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    .requestMatchers(
                        "/api/v1/auth/signup",
                        "/api/v1/auth/login",
                        "/api/v1/auth/reissue",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                    ).permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/deals/**").permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/products/**",
                        "/api/v1/categories/**",
                        "/api/v1/brands/**",
                        "/files/**",
                    ).permitAll()
                    .requestMatchers("/api/v1/admin/**").hasRole(Role.ADMIN.name)
                    .anyRequest().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    writeError(response, objectMapper, ErrorCode.UNAUTHORIZED)
                }
                it.accessDeniedHandler { _, response, _ ->
                    writeError(response, objectMapper, ErrorCode.FORBIDDEN)
                }
            }
            .addFilterBefore(JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    private fun managementServerRequest() = RequestMatcher { request ->
        val context = WebApplicationContextUtils.getWebApplicationContext(request.servletContext)
        context != null && WebServerApplicationContext.hasServerNamespace(context, "management")
    }

    private fun writeError(response: HttpServletResponse, objectMapper: ObjectMapper, code: ErrorCode) {
        response.status = code.httpStatus
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(objectMapper.writeValueAsString(ErrorResponse(code.name, code.message)))
    }
}
