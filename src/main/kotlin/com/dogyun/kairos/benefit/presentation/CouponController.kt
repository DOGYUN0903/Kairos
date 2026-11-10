package com.dogyun.kairos.benefit.presentation

import com.dogyun.kairos.benefit.application.CouponPolicyResult
import com.dogyun.kairos.benefit.application.CouponQueryService
import com.dogyun.kairos.benefit.application.CouponResult
import com.dogyun.kairos.benefit.application.CouponService
import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.presentation.PageResponse
import com.dogyun.kairos.common.security.LoginMember
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/coupons")
class CouponController(
    private val couponService: CouponService,
    private val couponQueryService: CouponQueryService,
) {

    @GetMapping("/policies")
    fun listClaimable(
        @AuthenticationPrincipal loginMember: LoginMember,
        @RequestParam(required = false) productId: Long?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<CouponPolicyResult>>> =
        ApiResponse.ok(PageResponse.from(couponQueryService.listClaimable(loginMember.id, productId, page, size)))

    @PostMapping("/policies/{policyId}/claim")
    fun claim(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable policyId: Long,
    ): ResponseEntity<ApiResponse<ClaimCouponResponse>> {
        val claimed = couponService.claim(loginMember.id, policyId)
        return ApiResponse.created(ClaimCouponResponse(claimed.couponId, claimed.expiresAt))
    }

    @GetMapping("/me")
    fun listMine(
        @AuthenticationPrincipal loginMember: LoginMember,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<CouponResult>>> =
        ApiResponse.ok(PageResponse.from(couponQueryService.listMine(loginMember.id, status, page, size)))
}
