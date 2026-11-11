package com.dogyun.kairos.catalog.presentation

import com.dogyun.kairos.catalog.application.AdminProductSummary
import com.dogyun.kairos.catalog.application.ProductAdminService
import com.dogyun.kairos.catalog.application.SellerProductDetail
import com.dogyun.kairos.catalog.domain.ProductStatus
import com.dogyun.kairos.common.domain.ListOrder
import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.presentation.PageResponse
import com.dogyun.kairos.common.security.LoginMember
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/products")
class AdminProductController(private val productAdminService: ProductAdminService) {

    @GetMapping
    fun list(
        @RequestParam(required = false) status: ProductStatus?,
        @RequestParam(defaultValue = "LATEST") sort: ListOrder,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<AdminProductSummary>>> =
        ApiResponse.ok(PageResponse.from(productAdminService.listForAdmin(status, sort, page, size)))

    @GetMapping("/{productId}")
    fun get(@PathVariable productId: Long): ResponseEntity<ApiResponse<SellerProductDetail>> =
        ApiResponse.ok(productAdminService.getForAdmin(productId))

    @PostMapping("/{productId}/approve")
    fun approve(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable productId: Long,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        productAdminService.approve(productId, loginMember.id)
        return ApiResponse.ok(null)
    }

    @PostMapping("/{productId}/reject")
    fun reject(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable productId: Long,
        @Valid @RequestBody request: RejectProductRequest,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        productAdminService.reject(productId, request.reason, loginMember.id)
        return ApiResponse.ok(null)
    }
}
