package com.dogyun.kairos.member.presentation

import com.dogyun.kairos.member.application.MemberMeResult
import com.dogyun.kairos.member.application.TokenResult

data class SignupResponse(val memberId: Long)

data class TokenResponse(val accessToken: String, val refreshToken: String) {
    companion object {
        fun from(result: TokenResult) = TokenResponse(result.accessToken, result.refreshToken)
    }
}

data class MeResponse(val memberId: Long, val email: String, val name: String, val role: String) {
    companion object {
        fun from(result: MemberMeResult) = MeResponse(result.memberId, result.email, result.name, result.role.name)
    }
}
