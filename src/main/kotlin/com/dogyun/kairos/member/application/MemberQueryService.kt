package com.dogyun.kairos.member.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.member.domain.MemberId
import com.dogyun.kairos.member.domain.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 다른 모듈(후기 작성자 표시 등)이 쓰는 회원 조회. */
@Service
class MemberQueryService(private val memberRepository: MemberRepository) {

    /** 로그인한 회원 자신의 정보. 토큰은 유효하지만 회원이 없으면 토큰 오류로 본다. */
    @Transactional(readOnly = true)
    fun getMe(memberId: Long): MemberMeResult {
        val member = memberRepository.findById(MemberId(memberId)) ?: throw DomainException(ErrorCode.AUTH_INVALID_TOKEN)
        return MemberMeResult(memberId, member.email.value, member.name, member.role)
    }

    /** memberId → 이름. 없는 회원은 빠진다. */
    @Transactional(readOnly = true)
    fun findNames(memberIds: Collection<Long>): Map<Long, String> =
        memberRepository.findAllByIds(memberIds.map(::MemberId)).associate { requireNotNull(it.id).value to it.name }
}
