package com.dogyun.kairos.member.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.security.LoginMember
import com.dogyun.kairos.member.application.AuthService
import com.dogyun.kairos.member.application.SignUpService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val signUpService: SignUpService,
    private val authService: AuthService,
) {
    @PostMapping("/signup")
    fun signUp(@Valid @RequestBody request: SignupRequest): ResponseEntity<ApiResponse<SignupResponse>> =
        ApiResponse.created(SignupResponse(signUpService.signUp(request.toCommand())))

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<ApiResponse<TokenResponse>> =
        ApiResponse.ok(TokenResponse.from(authService.login(request.toCommand())))

    @PostMapping("/reissue")
    fun reissue(@Valid @RequestBody request: ReissueRequest): ResponseEntity<ApiResponse<TokenResponse>> =
        ApiResponse.ok(TokenResponse.from(authService.reissue(request.refreshToken)))

    @PostMapping("/logout")
    fun logout(@AuthenticationPrincipal loginMember: LoginMember): ResponseEntity<Void> {
        authService.logout(loginMember.id)
        return ApiResponse.noContent()
    }
}
