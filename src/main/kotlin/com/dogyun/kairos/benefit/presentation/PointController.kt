package com.dogyun.kairos.benefit.presentation

import com.dogyun.kairos.benefit.application.PointQueryService
import com.dogyun.kairos.benefit.application.PointSummary
import com.dogyun.kairos.benefit.application.PointTransactionResult
import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.presentation.PageResponse
import com.dogyun.kairos.common.security.LoginMember
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/points/me")
class PointController(private val pointQueryService: PointQueryService) {

    @GetMapping
    fun getMine(@AuthenticationPrincipal loginMember: LoginMember): ResponseEntity<ApiResponse<PointSummary>> =
        ApiResponse.ok(pointQueryService.getMine(loginMember.id))

    @GetMapping("/transactions")
    fun listTransactions(
        @AuthenticationPrincipal loginMember: LoginMember,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<PointTransactionResult>>> =
        ApiResponse.ok(PageResponse.from(pointQueryService.listTransactions(loginMember.id, page, size)))
}
