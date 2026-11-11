package com.dogyun.kairos.member.presentation

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.security.AuthCookieProperties
import com.dogyun.kairos.common.security.JwtProvider
import com.dogyun.kairos.common.security.LoginMember
import com.dogyun.kairos.member.application.AuthService
import com.dogyun.kairos.member.application.MemberQueryService
import com.dogyun.kairos.member.application.SignUpService
import com.dogyun.kairos.member.application.TokenResult
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val signUpService: SignUpService,
    private val authService: AuthService,
    private val memberQueryService: MemberQueryService,
    private val jwtProvider: JwtProvider,
    private val cookieProperties: AuthCookieProperties,
) {
    @PostMapping("/signup")
    fun signUp(@Valid @RequestBody request: SignupRequest): ResponseEntity<ApiResponse<SignupResponse>> =
        ApiResponse.created(SignupResponse(signUpService.signUp(request.toCommand())))

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<ApiResponse<TokenResponse>> =
        tokenResponse(authService.login(request.toCommand()))

    /**
     * 본문 refreshToken 이 우선이다(k6·Swagger 등 기존 클라이언트). 없으면 쿠키를 쓰되,
     * 브라우저 CSRF 방어로 X-Requested-With: kairos-web 헤더가 있을 때만 받는다.
     */
    @PostMapping("/reissue")
    fun reissue(
        @RequestBody(required = false) request: ReissueRequest?,
        @CookieValue(REFRESH_COOKIE, required = false) cookie: String?,
        @RequestHeader("X-Requested-With", required = false) requestedWith: String?,
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        val token = request?.refreshToken?.takeIf { it.isNotBlank() }
            ?: cookie?.takeIf { it.isNotBlank() && requestedWith == REQUESTED_WITH }
            ?: throw DomainException(ErrorCode.INVALID_REQUEST)
        return tokenResponse(authService.reissue(token))
    }

    /** 액세스 토큰이 있으면 그 회원으로, 없으면 쿠키(+헤더)로 로그아웃한다. 어떤 경우든 쿠키를 만료시키고 204. */
    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal loginMember: LoginMember?,
        @CookieValue(REFRESH_COOKIE, required = false) cookie: String?,
        @RequestHeader("X-Requested-With", required = false) requestedWith: String?,
    ): ResponseEntity<Void> {
        if (loginMember != null) {
            authService.logout(loginMember.id)
        } else if (!cookie.isNullOrBlank() && requestedWith == REQUESTED_WITH) {
            authService.logoutByRefreshToken(cookie)
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookie(null).toString()).build()
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal loginMember: LoginMember): ResponseEntity<ApiResponse<MeResponse>> =
        ApiResponse.ok(MeResponse.from(memberQueryService.getMe(loginMember.id)))

    private fun tokenResponse(result: TokenResult): ResponseEntity<ApiResponse<TokenResponse>> =
        ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken).toString())
            .body(ApiResponse("success", TokenResponse.from(result)))

    /** token 이 null 이면 즉시 만료되는 삭제용 쿠키다. */
    private fun refreshCookie(token: String?): ResponseCookie =
        ResponseCookie.from(REFRESH_COOKIE, token ?: "")
            .httpOnly(true)
            .secure(cookieProperties.cookieSecure)
            .sameSite("Strict")
            .path("/api/v1/auth")
            .maxAge(if (token == null) 0 else jwtProvider.refreshTtl.seconds)
            .build()

    companion object {
        const val REFRESH_COOKIE = "kairos_refresh"
        const val REQUESTED_WITH = "kairos-web"
    }
}
