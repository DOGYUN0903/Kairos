package com.dogyun.kairos.review.application

import java.time.LocalDateTime

data class WriteReviewResult(val reviewId: Long, val rewardPoints: Long)

data class ReviewResult(
    val reviewId: Long,
    val orderItemId: Long,
    val productId: Long,
    val productName: String,
    val rating: Int,
    val content: String,
    val imageUrls: List<String>,
    val writerName: String,
    val replyContent: String?,
    val repliedAt: LocalDateTime?,
    val rewardPoints: Long,
    val createdAt: LocalDateTime,
    val modifiedAt: LocalDateTime,
)
