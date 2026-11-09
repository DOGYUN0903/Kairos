package com.dogyun.kairos.seller.infrastructure

import com.dogyun.kairos.seller.domain.ReviewTarget
import org.springframework.data.jpa.repository.JpaRepository

interface ReviewHistoryJpaRepository : JpaRepository<ReviewHistoryEntity, Long> {
    fun findAllByTargetTypeAndTargetIdOrderByIdAsc(targetType: ReviewTarget, targetId: Long): List<ReviewHistoryEntity>
}
