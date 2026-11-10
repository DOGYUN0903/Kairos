package com.dogyun.kairos.benefit.infrastructure

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MemberPointJpaRepository : JpaRepository<MemberPointEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MemberPointEntity p where p.memberId = :memberId")
    fun findLockedByMemberId(@Param("memberId") memberId: Long): MemberPointEntity?

    fun findByMemberId(memberId: Long): MemberPointEntity?
}
