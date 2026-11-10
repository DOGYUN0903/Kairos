package com.dogyun.kairos.review.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.presentation.PageResponse
import com.dogyun.kairos.common.security.AuthenticatedSeller
import com.dogyun.kairos.common.security.SellerPrincipal
import com.dogyun.kairos.review.application.ReviewQueryService
import com.dogyun.kairos.review.application.ReviewResult
import com.dogyun.kairos.review.application.ReviewService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/seller/reviews")
class SellerReviewController(
    private val reviewService: ReviewService,
    private val reviewQueryService: ReviewQueryService,
) {

    @GetMapping
    fun list(
        @AuthenticatedSeller(requireManage = true) seller: SellerPrincipal,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<ReviewResult>>> =
        ApiResponse.ok(PageResponse.from(reviewQueryService.listForSeller(seller.sellerId, page, size)))

    /** 덮어쓰기라 멱등이다(멱등 키 없음). */
    @PatchMapping("/{reviewId}/reply")
    fun reply(
        @AuthenticatedSeller(requireManage = true) seller: SellerPrincipal,
        @PathVariable reviewId: Long,
        @Valid @RequestBody request: ReplyReviewRequest,
    ): ResponseEntity<ApiResponse<ReviewResult>> {
        reviewService.reply(seller.sellerId, reviewId, request.content)
        return ApiResponse.ok(reviewQueryService.getForSeller(seller.sellerId, reviewId))
    }
}
