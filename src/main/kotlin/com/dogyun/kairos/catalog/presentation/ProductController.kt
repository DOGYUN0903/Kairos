package com.dogyun.kairos.catalog.presentation

import com.dogyun.kairos.catalog.application.LikedProductResult
import com.dogyun.kairos.catalog.application.ProductDetail
import com.dogyun.kairos.catalog.application.ProductLikeService
import com.dogyun.kairos.catalog.application.ProductSearchService
import com.dogyun.kairos.catalog.application.ProductSummary
import com.dogyun.kairos.catalog.domain.ProductSort
import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.presentation.PageResponse
import com.dogyun.kairos.common.security.LoginMember
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 비로그인 공개 상품 목록·검색·상세. 노출 조건은 상품 ON_SALE + 브랜드 APPROVED + 판매자 APPROVED. */
@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val productSearchService: ProductSearchService,
    private val productLikeService: ProductLikeService,
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) categoryId: Long?,
        @RequestParam(required = false) brandId: Long?,
        @RequestParam(required = false) minPrice: Long?,
        @RequestParam(required = false) maxPrice: Long?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "LATEST") sort: ProductSort,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal loginMember: LoginMember?,
    ): ResponseEntity<ApiResponse<PageResponse<ProductSummary>>> =
        ApiResponse.ok(PageResponse.from(productSearchService.list(categoryId, brandId, minPrice, maxPrice, keyword, sort, page, size, loginMember?.id)))

    /** `/me/likes` 는 리터럴 경로라 `/{productId}` 보다 우선 매핑된다. SecurityConfig 에서 인증을 요구한다. */
    @GetMapping("/me/likes")
    fun myLikes(
        @AuthenticationPrincipal loginMember: LoginMember,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<LikedProductResult>>> =
        ApiResponse.ok(PageResponse.from(productLikeService.listMine(loginMember.id, page, size)))

    @GetMapping("/{productId}")
    fun detail(
        @PathVariable productId: Long,
        @AuthenticationPrincipal loginMember: LoginMember?,
    ): ResponseEntity<ApiResponse<ProductDetail>> =
        ApiResponse.ok(productSearchService.detail(productId, loginMember?.id))

    @PutMapping("/{productId}/like")
    fun like(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable productId: Long,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        productLikeService.like(loginMember.id, productId)
        return ApiResponse.ok(null)
    }

    @DeleteMapping("/{productId}/like")
    fun unlike(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable productId: Long,
    ): ResponseEntity<Void> {
        productLikeService.unlike(loginMember.id, productId)
        return ApiResponse.noContent()
    }
}
