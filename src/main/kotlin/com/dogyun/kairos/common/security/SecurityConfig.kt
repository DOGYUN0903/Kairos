package com.dogyun.kairos.common.security

import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.presentation.ErrorResponse
import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import tools.jackson.databind.ObjectMapper

@Configuration
class SecurityConfig {

    @Bean
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
                        "/actuator/health",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                    ).permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/deals/**").permitAll()
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

    private fun writeError(response: HttpServletResponse, objectMapper: ObjectMapper, code: ErrorCode) {
        response.status = code.httpStatus
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(objectMapper.writeValueAsString(ErrorResponse(code.name, code.message)))
    }
}
