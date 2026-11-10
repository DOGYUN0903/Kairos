package com.dogyun.kairos.catalog.domain

/** 상품 집계(좋아요 수·후기 수·별점 합) 증감. 항상 UPDATE ... SET col = col + ? 한 문장이다. */
interface ProductStatsRepository {
    fun addLikeCount(productId: Long, delta: Long)
    fun addReview(productId: Long, countDelta: Long, ratingDelta: Long)
}
