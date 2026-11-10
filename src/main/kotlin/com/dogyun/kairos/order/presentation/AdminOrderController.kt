package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.presentation.PageResponse
import com.dogyun.kairos.order.application.OrderCancelService
import com.dogyun.kairos.order.application.OrderDetail
import com.dogyun.kairos.order.application.OrderQueryService
import com.dogyun.kairos.order.application.OrderSummary
import com.dogyun.kairos.order.domain.OrderStatus
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/orders")
class AdminOrderController(
    private val orderQueryService: OrderQueryService,
    private val orderCancelService: OrderCancelService,
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) status: OrderStatus?,
        @RequestParam(required = false) memberId: Long?,
        @RequestParam(required = false) orderNo: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<OrderSummary>>> =
        ApiResponse.ok(PageResponse.from(orderQueryService.listForAdmin(status, memberId, orderNo, page, size)))

    @GetMapping("/{orderId}")
    fun get(@PathVariable orderId: Long): ResponseEntity<ApiResponse<OrderDetail>> =
        ApiResponse.ok(orderQueryService.getForAdmin(orderId))

    /** `Idempotency-Key` 헤더는 필터가 검증·처리한다(IdempotencyProperties.paths). */
    @PostMapping("/{orderId}/items/{orderItemId}/cancel")
    fun cancelItem(
        @PathVariable orderId: Long,
        @PathVariable orderItemId: Long,
        @Valid @RequestBody request: CancelItemRequest,
    ): ResponseEntity<ApiResponse<CancelItemResponse>> = ApiResponse.ok(
        CancelItemResponse.from(orderCancelService.cancelByAdmin(orderId, orderItemId, request.reason)),
    )
}
