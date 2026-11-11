package com.dogyun.kairos.member.presentation

import com.dogyun.kairos.member.application.LoginCommand
import com.dogyun.kairos.member.application.SignUpCommand
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignupRequest(
    @field:NotBlank @field:Email val email: String,
    @field:Size(min = 8, max = 72) val password: String,
    @field:NotBlank @field:Size(max = 50) val name: String,
) {
    fun toCommand() = SignUpCommand(email, password, name)
}

data class LoginRequest(
    @field:NotBlank val email: String,
    @field:NotBlank val password: String,
) {
    fun toCommand() = LoginCommand(email, password)
}

/** refreshToken 이 없으면 쿠키 kairos_refresh 를 쓴다. */
data class ReissueRequest(val refreshToken: String? = null)
