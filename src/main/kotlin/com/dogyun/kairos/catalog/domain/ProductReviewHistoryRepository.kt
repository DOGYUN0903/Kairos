package com.dogyun.kairos.catalog.domain

interface ProductReviewHistoryRepository {
    fun save(history: ProductReviewHistory): ProductReviewHistory
    fun findAllByTarget(target: ProductReviewTarget, targetId: Long): List<ProductReviewHistory>
}
