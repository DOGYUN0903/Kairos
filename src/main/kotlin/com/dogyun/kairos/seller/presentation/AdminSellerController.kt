package com.dogyun.kairos.seller.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.presentation.PageResponse
import com.dogyun.kairos.common.security.LoginMember
import com.dogyun.kairos.seller.application.SellerAdminService
import com.dogyun.kairos.seller.application.SellerDetail
import com.dogyun.kairos.seller.application.SellerQueryService
import com.dogyun.kairos.seller.domain.SellerStatus
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
@RequestMapping("/api/v1/admin/sellers")
class AdminSellerController(
    private val sellerAdminService: SellerAdminService,
    private val sellerQueryService: SellerQueryService,
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) status: SellerStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<SellerDetail>>> =
        ApiResponse.ok(PageResponse.from(sellerQueryService.listForAdmin(status, page, size)))

    @PostMapping("/{sellerId}/approve")
    fun approve(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable sellerId: Long,
        @Valid @RequestBody request: ApproveSellerRequest,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        sellerAdminService.approve(sellerId, request.commissionRateBp, loginMember.id)
        return ApiResponse.ok(null)
    }

    @PostMapping("/{sellerId}/reject")
    fun reject(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable sellerId: Long,
        @Valid @RequestBody request: ReasonRequest,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        sellerAdminService.reject(sellerId, request.reason, loginMember.id)
        return ApiResponse.ok(null)
    }

    @PostMapping("/{sellerId}/suspend")
    fun suspend(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable sellerId: Long,
        @Valid @RequestBody request: ReasonRequest,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        sellerAdminService.suspend(sellerId, request.reason, loginMember.id)
        return ApiResponse.ok(null)
    }

    @PostMapping("/{sellerId}/unsuspend")
    fun unsuspend(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable sellerId: Long,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        sellerAdminService.unsuspend(sellerId, loginMember.id)
        return ApiResponse.ok(null)
    }
}
