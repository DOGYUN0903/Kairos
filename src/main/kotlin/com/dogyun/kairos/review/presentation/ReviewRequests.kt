package com.dogyun.kairos.review.presentation

import com.dogyun.kairos.review.application.WriteReviewCommand
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

/** 작성·수정 공통. 길이·사진 수·소유는 도메인이 `REVIEW_INVALID` 로 판정한다. */
data class WriteReviewRequest(
    @field:Min(1) @field:Max(5)
    val rating: Int,
    @field:NotBlank
    val content: String,
    val imageFileIds: List<Long> = emptyList(),
) {
    fun toCommand() = WriteReviewCommand(rating, content, imageFileIds)
}

/** 판매자 답글. 길이(최대 500자)는 도메인이 `REVIEW_INVALID` 로 판정한다. */
data class ReplyReviewRequest(@field:NotBlank val content: String)
