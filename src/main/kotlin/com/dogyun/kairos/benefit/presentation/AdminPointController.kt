package com.dogyun.kairos.benefit.presentation

import com.dogyun.kairos.benefit.application.PointQueryService
import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.presentation.PageResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/members/{memberId}/points")
class AdminPointController(private val pointQueryService: PointQueryService) {

    @GetMapping
    fun get(
        @PathVariable memberId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<AdminMemberPointsResponse>> {
        val result = pointQueryService.getForAdmin(memberId, page, size)
        return ApiResponse.ok(
            AdminMemberPointsResponse(result.balance, result.expiringWithin30Days, PageResponse.from(result.transactions)),
        )
    }
}
