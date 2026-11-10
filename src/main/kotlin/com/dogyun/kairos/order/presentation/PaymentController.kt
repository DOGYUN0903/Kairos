package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.security.LoginMember
import com.dogyun.kairos.order.application.OrderPaymentService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(private val orderPaymentService: OrderPaymentService) {

    /** `Idempotency-Key` 헤더는 필터가 검증·처리한다(IdempotencyProperties.paths). */
    @PostMapping("/confirm")
    fun confirm(
        @AuthenticationPrincipal loginMember: LoginMember,
        @Valid @RequestBody request: ConfirmPaymentRequest,
    ): ResponseEntity<ApiResponse<ConfirmPaymentResponse>> =
        ApiResponse.ok(ConfirmPaymentResponse.from(orderPaymentService.confirm(loginMember.id, request.toCommand())))
}
