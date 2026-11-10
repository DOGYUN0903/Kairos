package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.presentation.PageResponse
import com.dogyun.kairos.order.application.OrderReturnService
import com.dogyun.kairos.order.application.ReturnQueryService
import com.dogyun.kairos.order.application.ReturnRequestResult
import com.dogyun.kairos.order.domain.ReturnStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 관리자 반품 API(admin 경로는 ADMIN 권한만 접근한다). */
@RestController
@RequestMapping("/api/v1/admin/returns")
class AdminReturnController(
    private val orderReturnService: OrderReturnService,
    private val returnQueryService: ReturnQueryService,
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) status: ReturnStatus?,
        @RequestParam(required = false) sellerId: Long?,
        @RequestParam(required = false) memberId: Long?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<ReturnRequestResult>>> =
        ApiResponse.ok(PageResponse.from(returnQueryService.listForAdmin(status, sellerId, memberId, page, size)))

    @GetMapping("/{returnId}")
    fun get(@PathVariable returnId: Long): ResponseEntity<ApiResponse<ReturnRequestResult>> =
        ApiResponse.ok(returnQueryService.getForAdmin(returnId))

    @PatchMapping("/{returnId}/approve")
    fun approve(@PathVariable returnId: Long): ResponseEntity<ApiResponse<ReturnRequestResult>> =
        ApiResponse.ok(orderReturnService.approveByAdmin(returnId))

    @PatchMapping("/{returnId}/receive")
    fun receive(@PathVariable returnId: Long): ResponseEntity<ApiResponse<ReturnRequestResult>> =
        ApiResponse.ok(orderReturnService.receiveByAdmin(returnId))

    @PatchMapping("/{returnId}/cancel")
    fun cancel(@PathVariable returnId: Long): ResponseEntity<ApiResponse<ReturnRequestResult>> =
        ApiResponse.ok(orderReturnService.cancelByAdmin(returnId))
}
