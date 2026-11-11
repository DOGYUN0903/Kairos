package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.presentation.PageResponse
import com.dogyun.kairos.common.security.AuthenticatedSeller
import com.dogyun.kairos.common.security.SellerPrincipal
import com.dogyun.kairos.order.application.OrderReturnService
import com.dogyun.kairos.order.application.ReturnQueryService
import com.dogyun.kairos.order.application.ReturnRequestResult
import com.dogyun.kairos.order.domain.ReturnStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 판매자 반품 API. 조회는 정지된 판매자도 할 수 있고, 처리(승인·거절·수거 확인)는 정상 판매자만 한다. 남의 반품은 404. */
@RestController
@RequestMapping("/api/v1/seller/returns")
class SellerReturnController(
    private val orderReturnService: OrderReturnService,
    private val returnQueryService: ReturnQueryService,
) {

    @GetMapping
    fun list(
        @AuthenticatedSeller(requireManage = false) seller: SellerPrincipal,
        @RequestParam(required = false) status: ReturnStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<ReturnRequestResult>>> =
        ApiResponse.ok(PageResponse.from(returnQueryService.listForSeller(seller.sellerId, status, page, size)))

    @GetMapping("/{returnId}")
    fun get(
        @AuthenticatedSeller(requireManage = false) seller: SellerPrincipal,
        @PathVariable returnId: Long,
    ): ResponseEntity<ApiResponse<ReturnRequestResult>> = ApiResponse.ok(returnQueryService.getForSeller(seller.sellerId, returnId))

    @PatchMapping("/{returnId}/approve")
    fun approve(
        @AuthenticatedSeller seller: SellerPrincipal,
        @PathVariable returnId: Long,
    ): ResponseEntity<ApiResponse<ReturnRequestResult>> = ApiResponse.ok(orderReturnService.approveBySeller(seller.sellerId, returnId))

    @PatchMapping("/{returnId}/reject")
    fun reject(
        @AuthenticatedSeller seller: SellerPrincipal,
        @PathVariable returnId: Long,
        @RequestBody request: RejectReturnRequest,
    ): ResponseEntity<ApiResponse<ReturnRequestResult>> =
        ApiResponse.ok(orderReturnService.rejectBySeller(seller.sellerId, returnId, request.rejectReason ?: ""))

    @PatchMapping("/{returnId}/receive")
    fun receive(
        @AuthenticatedSeller seller: SellerPrincipal,
        @PathVariable returnId: Long,
    ): ResponseEntity<ApiResponse<ReturnRequestResult>> = ApiResponse.ok(orderReturnService.receiveBySeller(seller.sellerId, returnId))
}
