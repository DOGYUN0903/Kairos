package com.dogyun.kairos.member.presentation

import com.dogyun.kairos.member.application.TokenResult

data class SignupResponse(val memberId: Long)

data class TokenResponse(val accessToken: String, val refreshToken: String) {
    companion object {
        fun from(result: TokenResult) = TokenResponse(result.accessToken, result.refreshToken)
    }
}
