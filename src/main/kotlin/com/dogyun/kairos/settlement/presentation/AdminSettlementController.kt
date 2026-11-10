package com.dogyun.kairos.settlement.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.presentation.PageResponse
import com.dogyun.kairos.common.security.LoginMember
import com.dogyun.kairos.settlement.application.GenerateSettlementResult
import com.dogyun.kairos.settlement.application.SettlementAdjustmentResult
import com.dogyun.kairos.settlement.application.SettlementAdjustmentService
import com.dogyun.kairos.settlement.application.SettlementAdminService
import com.dogyun.kairos.settlement.application.SettlementDetail
import com.dogyun.kairos.settlement.application.SettlementGenerationService
import com.dogyun.kairos.settlement.application.SettlementSummary
import com.dogyun.kairos.settlement.domain.SettlementStatus
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 관리자 정산 API(admin 경로는 ADMIN 권한만 접근한다). */
@RestController
@RequestMapping("/api/v1/admin")
class AdminSettlementController(
    private val generationService: SettlementGenerationService,
    private val adminService: SettlementAdminService,
    private val adjustmentService: SettlementAdjustmentService,
) {

    @PostMapping("/settlements/generate")
    fun generate(@Valid @RequestBody request: GenerateSettlementRequest): ResponseEntity<ApiResponse<GenerateSettlementResult>> =
        ApiResponse.ok(generationService.generate(request.toCommand().periodStart))

    @GetMapping("/settlements")
    fun list(
        @RequestParam(required = false) sellerId: Long?,
        @RequestParam(required = false) status: SettlementStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<SettlementSummary>>> =
        ApiResponse.ok(PageResponse.from(adminService.list(sellerId, status, page, size)))

    @GetMapping("/settlements/{settlementId}")
    fun get(@PathVariable settlementId: Long): ResponseEntity<ApiResponse<SettlementDetail>> =
        ApiResponse.ok(adminService.get(settlementId))

    @PatchMapping("/settlements/{settlementId}/confirm")
    fun confirm(@PathVariable settlementId: Long): ResponseEntity<ApiResponse<SettlementDetail>> =
        ApiResponse.ok(adminService.confirm(settlementId))

    @PatchMapping("/settlements/{settlementId}/pay")
    fun pay(
        @PathVariable settlementId: Long,
        @RequestBody(required = false) request: PaySettlementRequest?,
    ): ResponseEntity<ApiResponse<SettlementDetail>> =
        ApiResponse.ok(adminService.markPaid(settlementId, request?.memo))

    @PostMapping("/sellers/{sellerId}/settlement-adjustments")
    fun createAdjustment(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable sellerId: Long,
        @Valid @RequestBody request: CreateAdjustmentRequest,
    ): ResponseEntity<ApiResponse<CreateAdjustmentResponse>> =
        ApiResponse.created(CreateAdjustmentResponse(adjustmentService.create(loginMember.id, sellerId, request.toCommand())))

    @GetMapping("/sellers/{sellerId}/settlement-adjustments")
    fun listAdjustments(
        @PathVariable sellerId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<SettlementAdjustmentResult>>> =
        ApiResponse.ok(PageResponse.from(adjustmentService.listBySeller(sellerId, page, size)))

    @DeleteMapping("/settlement-adjustments/{adjustmentId}")
    fun deleteAdjustment(@PathVariable adjustmentId: Long): ResponseEntity<Void> {
        adjustmentService.delete(adjustmentId)
        return ApiResponse.noContent()
    }
}
