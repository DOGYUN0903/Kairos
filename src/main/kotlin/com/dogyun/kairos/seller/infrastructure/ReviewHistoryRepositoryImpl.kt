package com.dogyun.kairos.seller.infrastructure

import com.dogyun.kairos.seller.domain.ReviewHistory
import com.dogyun.kairos.seller.domain.ReviewHistoryRepository
import com.dogyun.kairos.seller.domain.ReviewTarget
import org.springframework.stereotype.Repository

@Repository
class ReviewHistoryRepositoryImpl(private val jpaRepository: ReviewHistoryJpaRepository) : ReviewHistoryRepository {

    /** 심사 이력은 추가만 한다. */
    override fun save(history: ReviewHistory): ReviewHistory {
        require(history.id == null) { "이미 저장된 심사 이력은 다시 저장할 수 없습니다." }
        val entity = ReviewHistoryEntity(
            history.target, history.targetId, history.result, history.reason, history.reviewerId, history.reviewedAt,
        )
        return toDomain(jpaRepository.saveAndFlush(entity))
    }

    override fun findAllByTarget(target: ReviewTarget, targetId: Long): List<ReviewHistory> =
        jpaRepository.findAllByTargetTypeAndTargetIdOrderByIdAsc(target, targetId).map(::toDomain)

    private fun toDomain(entity: ReviewHistoryEntity) = ReviewHistory(
        id = entity.id,
        target = entity.targetType,
        targetId = entity.targetId,
        result = entity.result,
        reason = entity.reason,
        reviewerId = entity.reviewerId,
        reviewedAt = entity.reviewedAt,
    )
}
