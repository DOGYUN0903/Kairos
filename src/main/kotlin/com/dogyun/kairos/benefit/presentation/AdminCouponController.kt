package com.dogyun.kairos.benefit.presentation

import com.dogyun.kairos.benefit.application.CouponPolicyResult
import com.dogyun.kairos.benefit.application.CouponPolicyService
import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.presentation.PageResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/coupons/policies")
class AdminCouponController(private val policyService: CouponPolicyService) {

    @PostMapping
    fun create(@Valid @RequestBody request: CreateCouponPolicyRequest): ResponseEntity<ApiResponse<CreateCouponPolicyResponse>> =
        ApiResponse.created(CreateCouponPolicyResponse(policyService.createByAdmin(request.toCommand())))

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<CouponPolicyResult>>> =
        ApiResponse.ok(PageResponse.from(policyService.listForAdmin(page, size)))

    @PatchMapping("/{policyId}/end")
    fun end(@PathVariable policyId: Long): ResponseEntity<ApiResponse<Nothing?>> {
        policyService.end(policyId)
        return ApiResponse.ok(null)
    }
}
