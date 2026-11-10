package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.ProductStatsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 후기 집계 갱신. review 모듈이 후기 작성·수정·삭제 트랜잭션 안에서 부른다. */
@Service
class ProductStatsService(private val statsRepository: ProductStatsRepository) {

    @Transactional
    fun applyReview(productId: Long, countDelta: Long, ratingDelta: Long) {
        statsRepository.addReview(productId, countDelta, ratingDelta)
    }
}
