package com.dogyun.kairos.member.domain

interface MemberRepository {
    /** 신규 회원을 저장한다. 이메일이 이미 있으면 MEMBER_EMAIL_DUPLICATED. */
    fun save(member: Member): Member
    fun findById(id: MemberId): Member?
    fun findByEmail(email: Email): Member?
    fun existsByEmail(email: Email): Boolean
}
