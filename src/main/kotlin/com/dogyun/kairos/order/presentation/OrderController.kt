package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.security.LoginMember
import com.dogyun.kairos.order.application.OrderQueryService
import com.dogyun.kairos.order.application.OrderService
import com.dogyun.kairos.order.application.OrderSummary
import com.dogyun.kairos.common.presentation.PageResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderService: OrderService,
    private val orderQueryService: OrderQueryService,
) {

    @PostMapping
    fun create(
        @AuthenticationPrincipal loginMember: LoginMember,
        @Valid @RequestBody request: CreateOrderRequest,
    ): ResponseEntity<ApiResponse<CreateOrderResponse>> =
        ApiResponse.created(CreateOrderResponse.from(orderService.place(request.toCommand(loginMember.id))))

    @PostMapping("/{orderId}/cancel")
    fun cancel(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable orderId: Long,
    ): ResponseEntity<ApiResponse<Unit?>> {
        orderService.cancel(loginMember.id, orderId)
        return ApiResponse.ok<Unit?>(null)
    }

    @GetMapping("/me")
    fun mine(
        @AuthenticationPrincipal loginMember: LoginMember,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<OrderSummary>>> =
        ApiResponse.ok(PageResponse.from(orderQueryService.findMine(loginMember.id, page, size)))
}
