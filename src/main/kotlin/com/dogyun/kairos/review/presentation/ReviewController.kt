package com.dogyun.kairos.review.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.presentation.PageResponse
import com.dogyun.kairos.common.security.LoginMember
import com.dogyun.kairos.review.application.ReviewQueryService
import com.dogyun.kairos.review.application.ReviewResult
import com.dogyun.kairos.review.application.ReviewService
import com.dogyun.kairos.review.application.WriteReviewResult
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ReviewController(
    private val reviewService: ReviewService,
    private val reviewQueryService: ReviewQueryService,
) {

    /** `Idempotency-Key` 는 공통 필터가 검사한다(IdempotencyProperties.paths). */
    @PostMapping("/api/v1/orders/{orderId}/items/{orderItemId}/reviews")
    fun write(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable orderId: Long,
        @PathVariable orderItemId: Long,
        @Valid @RequestBody request: WriteReviewRequest,
    ): ResponseEntity<ApiResponse<WriteReviewResult>> =
        ApiResponse.created(reviewService.write(loginMember.id, orderId, orderItemId, request.toCommand()))

    @GetMapping("/api/v1/reviews/me")
    fun listMine(
        @AuthenticationPrincipal loginMember: LoginMember,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<ReviewResult>>> =
        ApiResponse.ok(PageResponse.from(reviewQueryService.listMine(loginMember.id, page, size)))

    @PatchMapping("/api/v1/reviews/{reviewId}")
    fun edit(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable reviewId: Long,
        @Valid @RequestBody request: WriteReviewRequest,
    ): ResponseEntity<ApiResponse<ReviewResult>> {
        reviewService.edit(loginMember.id, reviewId, request.toCommand())
        return ApiResponse.ok(reviewQueryService.get(loginMember.id, reviewId))
    }

    @DeleteMapping("/api/v1/reviews/{reviewId}")
    fun delete(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable reviewId: Long,
    ): ResponseEntity<Void> {
        reviewService.delete(loginMember.id, reviewId)
        return ApiResponse.noContent()
    }

    /** 공개(SecurityConfig 의 GET `/api/v1/products/` 하위 permitAll). */
    @GetMapping("/api/v1/products/{productId}/reviews")
    fun listByProduct(
        @PathVariable productId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<ReviewResult>>> =
        ApiResponse.ok(PageResponse.from(reviewQueryService.listByProduct(productId, page, size)))
}
