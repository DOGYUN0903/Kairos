package com.dogyun.kairos.benefit.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface MemberPointJpaRepository : JpaRepository<MemberPointEntity, Long> {
    fun findByMemberId(memberId: Long): MemberPointEntity?
}
