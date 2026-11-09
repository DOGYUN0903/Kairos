package com.dogyun.kairos.catalog.domain

import java.time.LocalDateTime

enum class ProductReviewTarget { PRODUCT, CHANGE_REQUEST }

enum class ProductReviewResult { APPROVED, REJECTED }

/** 상품·변경 요청 심사 이력. 한 번 남기면 바뀌지 않는다. */
class ProductReviewHistory(
    val id: Long?,
    val target: ProductReviewTarget,
    val targetId: Long,
    val result: ProductReviewResult,
    val reason: String?,
    val reviewerId: Long,
    val reviewedAt: LocalDateTime,
)
