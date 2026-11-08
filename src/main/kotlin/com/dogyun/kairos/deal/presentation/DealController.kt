package com.dogyun.kairos.deal.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.presentation.PageResponse
import com.dogyun.kairos.deal.application.DealDetail
import com.dogyun.kairos.deal.application.DealSummary
import com.dogyun.kairos.deal.application.DealQueryService
import com.dogyun.kairos.deal.domain.DealStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/deals")
class DealController(private val dealQueryService: DealQueryService) {

    @GetMapping
    fun list(
        @RequestParam(required = false) status: DealStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<DealSummary>>> =
        ApiResponse.ok(PageResponse.from(dealQueryService.list(status, page, size)))

    @GetMapping("/{dealId}")
    fun detail(@PathVariable dealId: Long): ResponseEntity<ApiResponse<DealDetail>> =
        ApiResponse.ok(dealQueryService.detail(dealId))
}
