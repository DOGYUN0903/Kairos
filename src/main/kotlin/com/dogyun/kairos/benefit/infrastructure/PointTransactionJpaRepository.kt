package com.dogyun.kairos.benefit.infrastructure

import com.dogyun.kairos.benefit.domain.PointRefType
import com.dogyun.kairos.benefit.domain.PointTransactionType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface PointTransactionJpaRepository : JpaRepository<PointTransactionEntity, Long> {
    fun findByTransactionTypeAndRefTypeAndRefId(type: PointTransactionType, refType: PointRefType, refId: Long): PointTransactionEntity?
    fun findByMemberIdOrderByIdDesc(memberId: Long, pageable: Pageable): Page<PointTransactionEntity>
}
