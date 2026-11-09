package com.dogyun.kairos.seller.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.presentation.PageResponse
import com.dogyun.kairos.common.security.LoginMember
import com.dogyun.kairos.seller.application.BrandAdminService
import com.dogyun.kairos.seller.application.BrandDetail
import com.dogyun.kairos.seller.domain.BrandStatus
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
@RequestMapping("/api/v1/admin/brands")
class AdminBrandController(private val brandAdminService: BrandAdminService) {

    @GetMapping
    fun list(
        @RequestParam(required = false) status: BrandStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<BrandDetail>>> =
        ApiResponse.ok(PageResponse.from(brandAdminService.listForAdmin(status, page, size)))

    @PostMapping("/{brandId}/approve")
    fun approve(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable brandId: Long,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        brandAdminService.approve(brandId, loginMember.id)
        return ApiResponse.ok(null)
    }

    @PostMapping("/{brandId}/reject")
    fun reject(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable brandId: Long,
        @Valid @RequestBody request: ReasonRequest,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        brandAdminService.reject(brandId, request.reason, loginMember.id)
        return ApiResponse.ok(null)
    }
}
