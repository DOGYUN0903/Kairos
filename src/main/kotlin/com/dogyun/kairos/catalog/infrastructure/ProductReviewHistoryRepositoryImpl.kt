package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.catalog.domain.ProductReviewHistory
import com.dogyun.kairos.catalog.domain.ProductReviewHistoryRepository
import com.dogyun.kairos.catalog.domain.ProductReviewTarget
import org.springframework.stereotype.Repository

@Repository
class ProductReviewHistoryRepositoryImpl(
    private val jpaRepository: ProductReviewHistoryJpaRepository,
) : ProductReviewHistoryRepository {

    /** 심사 이력은 추가만 한다. */
    override fun save(history: ProductReviewHistory): ProductReviewHistory {
        require(history.id == null) { "이미 저장된 심사 이력은 다시 저장할 수 없습니다." }
        val entity = ProductReviewHistoryEntity(
            history.target, history.targetId, history.result, history.reason, history.reviewerId, history.reviewedAt,
        )
        return toDomain(jpaRepository.saveAndFlush(entity))
    }

    override fun findAllByTarget(target: ProductReviewTarget, targetId: Long): List<ProductReviewHistory> =
        jpaRepository.findAllByTargetTypeAndTargetIdOrderByIdAsc(target, targetId).map(::toDomain)

    private fun toDomain(entity: ProductReviewHistoryEntity) = ProductReviewHistory(
        id = entity.id,
        target = entity.targetType,
        targetId = entity.targetId,
        result = entity.result,
        reason = entity.reason,
        reviewerId = entity.reviewerId,
        reviewedAt = entity.reviewedAt,
    )
}
