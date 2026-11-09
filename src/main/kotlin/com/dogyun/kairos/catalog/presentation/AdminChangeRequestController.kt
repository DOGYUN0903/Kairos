package com.dogyun.kairos.catalog.presentation

import com.dogyun.kairos.catalog.application.ChangeRequestAdminService
import com.dogyun.kairos.catalog.application.ChangeRequestDetail
import com.dogyun.kairos.catalog.application.ChangeRequestSummary
import com.dogyun.kairos.catalog.domain.ChangeRequestStatus
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
@RequestMapping("/api/v1/admin/change-requests")
class AdminChangeRequestController(private val changeRequestAdminService: ChangeRequestAdminService) {

    @GetMapping
    fun list(
        @RequestParam(required = false) status: ChangeRequestStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<ChangeRequestSummary>>> =
        ApiResponse.ok(PageResponse.from(changeRequestAdminService.listForAdmin(status, page, size)))

    @GetMapping("/{changeRequestId}")
    fun get(@PathVariable changeRequestId: Long): ResponseEntity<ApiResponse<ChangeRequestDetail>> =
        ApiResponse.ok(changeRequestAdminService.get(changeRequestId))

    @PostMapping("/{changeRequestId}/approve")
    fun approve(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable changeRequestId: Long,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        changeRequestAdminService.approve(changeRequestId, loginMember.id)
        return ApiResponse.ok(null)
    }

    @PostMapping("/{changeRequestId}/reject")
    fun reject(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable changeRequestId: Long,
        @Valid @RequestBody request: RejectProductRequest,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        changeRequestAdminService.reject(changeRequestId, request.reason, loginMember.id)
        return ApiResponse.ok(null)
    }
}
