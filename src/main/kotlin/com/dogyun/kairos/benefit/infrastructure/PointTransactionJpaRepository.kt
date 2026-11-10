package com.dogyun.kairos.benefit.infrastructure

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface PointTransactionJpaRepository : JpaRepository<PointTransactionEntity, Long> {
    fun findByMemberIdOrderByIdDesc(memberId: Long, pageable: Pageable): Page<PointTransactionEntity>
}
