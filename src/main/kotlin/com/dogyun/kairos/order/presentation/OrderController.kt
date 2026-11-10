package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.security.LoginMember
import com.dogyun.kairos.order.application.OrderService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(private val orderService: OrderService) {

    /** `Idempotency-Key` 헤더는 필터가 검증·처리한다(IdempotencyProperties.paths). */
    @PostMapping
    fun create(
        @AuthenticationPrincipal loginMember: LoginMember,
        @Valid @RequestBody request: CreateOrderRequest,
    ): ResponseEntity<ApiResponse<CreateOrderResponse>> =
        ApiResponse.created(CreateOrderResponse.from(orderService.place(loginMember.id, request.toCommand())))
}
