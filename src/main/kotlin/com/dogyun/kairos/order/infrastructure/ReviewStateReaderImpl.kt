package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.order.application.ItemReviewState
import com.dogyun.kairos.order.application.ReviewCandidate
import com.dogyun.kairos.order.application.ReviewStateReader
import com.dogyun.kairos.review.application.ReviewStateQueryService
import org.springframework.stereotype.Component

/** [ReviewStateQueryService] 만 부른다. ReviewService 는 주문 조회에 의존하므로 주입하면 빈 순환이 생긴다. */
@Component
class ReviewStateReaderImpl(private val reviewStateQueryService: ReviewStateQueryService) : ReviewStateReader {
    override fun statesOf(candidates: List<ReviewCandidate>): Map<Long, ItemReviewState> =
        reviewStateQueryService.statesOf(candidates.map { it.orderItemId to it.confirmedAt })
            .mapValues { (_, s) -> ItemReviewState(s.status, s.reviewId, s.writableUntil) }
}
