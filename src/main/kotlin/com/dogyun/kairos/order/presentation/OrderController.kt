package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.security.LoginMember
import com.dogyun.kairos.common.presentation.PageResponse
import com.dogyun.kairos.order.application.OrderCancelService
import com.dogyun.kairos.order.application.OrderDetail
import com.dogyun.kairos.order.application.OrderQueryService
import com.dogyun.kairos.order.application.OrderShippingService
import com.dogyun.kairos.order.application.OrderService
import com.dogyun.kairos.order.application.OrderSummary
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderService: OrderService,
    private val orderQueryService: OrderQueryService,
    private val orderCancelService: OrderCancelService,
    private val orderShippingService: OrderShippingService,
) {

    /** `Idempotency-Key` 헤더는 필터가 검증·처리한다(IdempotencyProperties.paths). */
    @PostMapping
    fun create(
        @AuthenticationPrincipal loginMember: LoginMember,
        @Valid @RequestBody request: CreateOrderRequest,
    ): ResponseEntity<ApiResponse<CreateOrderResponse>> =
        ApiResponse.created(CreateOrderResponse.from(orderService.place(loginMember.id, request.toCommand())))

    @GetMapping
    fun listMine(
        @AuthenticationPrincipal loginMember: LoginMember,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<OrderSummary>>> =
        ApiResponse.ok(PageResponse.from(orderQueryService.listMine(loginMember.id, page, size)))

    @GetMapping("/{orderId}")
    fun getMine(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable orderId: Long,
    ): ResponseEntity<ApiResponse<OrderDetail>> = ApiResponse.ok(orderQueryService.getMine(loginMember.id, orderId))

    /** `Idempotency-Key` 헤더는 필터가 검증·처리한다(IdempotencyProperties.paths). */
    @PostMapping("/{orderId}/items/{orderItemId}/cancel")
    fun cancelItem(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable orderId: Long,
        @PathVariable orderItemId: Long,
        @Valid @RequestBody request: CancelItemRequest,
    ): ResponseEntity<ApiResponse<CancelItemResponse>> = ApiResponse.ok(
        CancelItemResponse.from(orderCancelService.cancelByBuyer(loginMember.id, orderId, orderItemId, request.reason)),
    )

    @PatchMapping("/{orderId}/seller-orders/{sellerOrderId}/confirm")
    fun confirmPurchase(
        @AuthenticationPrincipal loginMember: LoginMember,
        @PathVariable orderId: Long,
        @PathVariable sellerOrderId: Long,
    ): ResponseEntity<ApiResponse<SellerOrderStateResponse>> = ApiResponse.ok(
        SellerOrderStateResponse.from(orderShippingService.confirmPurchase(loginMember.id, orderId, sellerOrderId)),
    )
}
