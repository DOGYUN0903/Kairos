package com.dogyun.kairos.seller.domain

import java.time.LocalDateTime

enum class ReviewTarget { SELLER, BRAND }

enum class ReviewResult { APPROVED, REJECTED, SUSPENDED, UNSUSPENDED }

/** 판매자·브랜드 심사 이력. 한 번 남기면 바뀌지 않는다. */
class ReviewHistory(
    val id: Long?,
    val target: ReviewTarget,
    val targetId: Long,
    val result: ReviewResult,
    val reason: String?,
    val reviewerId: Long,
    val reviewedAt: LocalDateTime,
)
