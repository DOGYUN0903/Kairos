package com.dogyun.kairos.review.application

import com.dogyun.kairos.review.domain.ReviewRepository
import com.dogyun.kairos.review.domain.reviewWritableUntil
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

/**
 * 주문 항목들의 후기 상태(`WRITABLE`/`WRITTEN`/`EXPIRED`/`DELETED`). `NOT_ELIGIBLE` 은 order 가 정한다.
 * order 의 포트 구현이 부르므로 [ReviewService](주문 조회에 의존)나 order 쪽으로 가는 의존을 두지 않는다(빈 순환).
 */
@Service
@Transactional(readOnly = true)
class ReviewStateQueryService(
    private val reviewRepository: ReviewRepository,
    private val properties: ReviewProperties,
    private val clock: Clock,
) {

    /** [candidates] 는 (주문 항목 ID, 구매 확정 시각). 저장소는 한 번만 읽는다. */
    fun statesOf(candidates: List<Pair<Long, LocalDateTime>>): Map<Long, ReviewStateResult> {
        if (candidates.isEmpty()) return emptyMap()
        val rows = reviewRepository.findStatesByOrderItemIds(candidates.map { it.first })
        val now = LocalDateTime.now(clock)
        return candidates.associate { (orderItemId, confirmedAt) ->
            val row = rows[orderItemId]
            orderItemId to when {
                row == null -> {
                    val until = reviewWritableUntil(confirmedAt, properties.writeDeadline)
                    ReviewStateResult(if (until.isBefore(now)) "EXPIRED" else "WRITABLE", null, until)
                }
                row.deleted -> ReviewStateResult("DELETED", null, null)
                else -> ReviewStateResult("WRITTEN", row.reviewId, null)
            }
        }
    }
}

data class ReviewStateResult(val status: String, val reviewId: Long?, val writableUntil: LocalDateTime?)
