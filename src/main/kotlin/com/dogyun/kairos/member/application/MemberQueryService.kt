package com.dogyun.kairos.member.application

import com.dogyun.kairos.member.domain.MemberId
import com.dogyun.kairos.member.domain.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 다른 모듈(후기 작성자 표시 등)이 쓰는 회원 조회. */
@Service
class MemberQueryService(private val memberRepository: MemberRepository) {

    /** memberId → 이름. 없는 회원은 빠진다. */
    @Transactional(readOnly = true)
    fun findNames(memberIds: Collection<Long>): Map<Long, String> =
        memberRepository.findAllByIds(memberIds.map(::MemberId)).associate { requireNotNull(it.id).value to it.name }
}
