package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.catalog.domain.ProductStatsRepository
import org.springframework.stereotype.Repository

@Repository
class ProductStatsRepositoryImpl(private val productJpaRepository: ProductJpaRepository) : ProductStatsRepository {

    override fun addLikeCount(productId: Long, delta: Long) {
        productJpaRepository.addLikeCount(productId, delta)
    }

    override fun addReview(productId: Long, countDelta: Long, ratingDelta: Long) {
        productJpaRepository.addReview(productId, countDelta, ratingDelta)
    }
}
