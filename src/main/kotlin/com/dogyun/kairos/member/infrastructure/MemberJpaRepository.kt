package com.dogyun.kairos.member.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface MemberJpaRepository : JpaRepository<MemberEntity, Long> {
    fun findByEmail(email: String): MemberEntity?
    fun existsByEmail(email: String): Boolean
}
