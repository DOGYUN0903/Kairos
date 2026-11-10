package com.dogyun.kairos.review.application

/** benefit 의 후기 적립 포트. 원장 키 (EARN_REVIEW, REVIEW, reviewId) 로 멱등이며 이미 적립했으면 0 을 돌려준다. */
interface ReviewRewardGranter {
    fun grantForReview(memberId: Long, reviewId: Long, hasPhoto: Boolean): Long
}
