package com.dogyun.kairos.order.application

import java.time.LocalDateTime

/** review 모듈의 후기 상태 조회 포트. 구현은 order.infrastructure 가 review application 서비스로 한다. */
interface ReviewStateReader {
    /** 후보 항목 ID → 후기 상태(`WRITABLE`/`WRITTEN`/`EXPIRED`/`DELETED`). 한 번의 호출로 모두 묻는다. */
    fun statesOf(candidates: List<ReviewCandidate>): Map<Long, ItemReviewState>
}

/** 구매 확정된 판매자 주문의 `ORDERED` 항목. */
data class ReviewCandidate(val orderItemId: Long, val confirmedAt: LocalDateTime)

data class ItemReviewState(val status: String, val reviewId: Long?, val writableUntil: LocalDateTime?)
