package com.dogyun.kairos.review.infrastructure

import com.dogyun.kairos.benefit.application.PointService
import com.dogyun.kairos.review.application.ReviewRewardGranter
import org.springframework.stereotype.Component

@Component
class ReviewRewardGranterImpl(private val pointService: PointService) : ReviewRewardGranter {
    override fun grantForReview(memberId: Long, reviewId: Long, hasPhoto: Boolean): Long =
        pointService.earnForReview(memberId, reviewId, hasPhoto)
}
