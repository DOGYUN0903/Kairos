package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.presentation.PageResponse
import com.dogyun.kairos.common.security.LoginMember
import com.dogyun.kairos.order.application.OrderReturnService
import com.dogyun.kairos.order.application.ReturnQueryService
import com.dogyun.kairos.order.application.ReturnRequestResult
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 구매자 반품 API. */
@RestController
class ReturnController(
    private val orderReturnService: OrderReturnService,
    private val returnQueryService: ReturnQueryService,
) {

    /** `Idempotency-Key` 헤더는 필터가 검증·처리한다(IdempotencyProperties.paths). */
    @PostMapping("/api/v1/orders/{orderId}/items/{orderItemId}/returns")
    fun request(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable orderId: Long,
        @PathVariable orderItemId: Long,
        @Valid @RequestBody request: RequestReturnRequest,
    ): ResponseEntity<ApiResponse<ReturnRequestResult>> =
        ApiResponse.created(orderReturnService.request(loginMember.id, orderId, orderItemId, request.toCommand()))

    /** 상태를 바꾸지 않는 읽기라 `Idempotency-Key` 가 없다. 사유는 반품 요청과 같은 변환을 거치도록 문자열로 받는다. */
    @GetMapping("/api/v1/orders/{orderId}/items/{orderItemId}/return-preview")
    fun preview(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable orderId: Long,
        @PathVariable orderItemId: Long,
        @RequestParam reason: String,
    ): ResponseEntity<ApiResponse<ReturnPreviewResponse>> = ApiResponse.ok(
        ReturnPreviewResponse.from(returnQueryService.previewReturn(loginMember.id, orderId, orderItemId, reason)),
    )

    @GetMapping("/api/v1/returns/me")
    fun listMine(
        @AuthenticationPrincipal loginMember: LoginMember,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<ReturnRequestResult>>> =
        ApiResponse.ok(PageResponse.from(returnQueryService.listMine(loginMember.id, page, size)))

    @GetMapping("/api/v1/returns/{returnId}")
    fun getMine(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable returnId: Long,
    ): ResponseEntity<ApiResponse<ReturnRequestResult>> = ApiResponse.ok(returnQueryService.getMine(loginMember.id, returnId))

    @DeleteMapping("/api/v1/returns/{returnId}")
    fun withdraw(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable returnId: Long,
    ): ResponseEntity<ApiResponse<ReturnRequestResult>> = ApiResponse.ok(orderReturnService.withdraw(loginMember.id, returnId))
}
