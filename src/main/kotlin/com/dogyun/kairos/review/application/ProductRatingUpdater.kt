package com.dogyun.kairos.review.application

/** catalog 의 상품 후기 집계(review_count, rating_sum) 증감 포트. 후기 트랜잭션 안에서 부른다. */
interface ProductRatingUpdater {
    fun applyReview(productId: Long, countDelta: Long, ratingDelta: Long)
}
