package com.dogyun.kairos.member.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.security.Role
import com.dogyun.kairos.member.domain.Email
import com.dogyun.kairos.member.domain.Member
import com.dogyun.kairos.member.domain.MemberRepository
import com.dogyun.kairos.member.domain.PasswordHasher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SignUpService(
    private val memberRepository: MemberRepository,
    private val passwordHasher: PasswordHasher,
) {
    @Transactional
    fun signUp(command: SignUpCommand): Long {
        val email = Email(command.email)
        // 동시 가입 경합은 UNIQUE 제약이 막고, 저장소가 같은 에러 코드로 변환한다.
        if (memberRepository.existsByEmail(email)) throw DomainException(ErrorCode.MEMBER_EMAIL_DUPLICATED)
        val member = Member.register(email, command.password, command.name, Role.USER, passwordHasher)
        return memberRepository.save(member).id!!.value
    }
}
