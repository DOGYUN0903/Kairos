package com.dogyun.kairos.member.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.security.JwtProvider
import com.dogyun.kairos.member.domain.Email
import com.dogyun.kairos.member.domain.Member
import com.dogyun.kairos.member.domain.MemberId
import com.dogyun.kairos.member.domain.MemberRepository
import com.dogyun.kairos.member.domain.PasswordHasher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val memberRepository: MemberRepository,
    private val passwordHasher: PasswordHasher,
    private val jwtProvider: JwtProvider,
    private val refreshTokenStore: RefreshTokenStore,
) {
    @Transactional(readOnly = true)
    fun login(command: LoginCommand): TokenResult {
        // 이메일 형식 오류, 없는 이메일, 틀린 비밀번호를 구분하지 않는다(계정 존재 여부 노출 방지).
        val invalid = DomainException(ErrorCode.AUTH_INVALID_CREDENTIALS)
        val email = runCatching { Email(command.email) }.getOrElse { throw invalid }
        val member = memberRepository.findByEmail(email) ?: throw invalid
        if (!member.matchesPassword(command.password, passwordHasher)) throw invalid
        return issueTokens(member)
    }

    /** 리프레시 토큰을 회전한다: 저장된 토큰과 일치할 때만 새 토큰으로 교체하고 옛 토큰은 무효가 된다. */
    @Transactional(readOnly = true)
    fun reissue(refreshToken: String): TokenResult {
        val invalid = DomainException(ErrorCode.AUTH_INVALID_TOKEN)
        val claims = jwtProvider.parseRefreshToken(refreshToken) ?: throw invalid
        if (refreshTokenStore.find(claims.memberId) != refreshToken) throw invalid
        val member = memberRepository.findById(MemberId(claims.memberId)) ?: throw invalid
        return issueTokens(member)
    }

    fun logout(memberId: Long) {
        refreshTokenStore.delete(memberId)
    }

    private fun issueTokens(member: Member): TokenResult {
        val memberId = member.id!!.value
        val refreshToken = jwtProvider.createRefreshToken(memberId)
        refreshTokenStore.save(memberId, refreshToken, jwtProvider.refreshTtl)
        return TokenResult(jwtProvider.createAccessToken(memberId, member.role), refreshToken)
    }
}
