package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.catalog.domain.ProductReviewTarget
import org.springframework.data.jpa.repository.JpaRepository

interface ProductReviewHistoryJpaRepository : JpaRepository<ProductReviewHistoryEntity, Long> {
    fun findAllByTargetTypeAndTargetIdOrderByIdAsc(
        targetType: ProductReviewTarget,
        targetId: Long,
    ): List<ProductReviewHistoryEntity>
}
