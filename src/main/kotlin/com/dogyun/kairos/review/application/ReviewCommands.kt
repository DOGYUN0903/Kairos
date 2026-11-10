package com.dogyun.kairos.review.application

data class WriteReviewCommand(
    val rating: Int,
    val content: String,
    val imageFileIds: List<Long>,
)
