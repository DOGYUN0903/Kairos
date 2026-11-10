package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.presentation.PageResponse
import com.dogyun.kairos.common.security.AuthenticatedSeller
import com.dogyun.kairos.common.security.SellerPrincipal
import com.dogyun.kairos.order.application.OrderCancelService
import com.dogyun.kairos.order.application.OrderQueryService
import com.dogyun.kairos.order.application.SellerOrderDetail
import com.dogyun.kairos.order.application.SellerOrderSummary
import com.dogyun.kairos.order.domain.SellerOrderStatus
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 조회는 정지된 판매자도 할 수 있다. 취소는 정상 판매자만 한다. */
@RestController
@RequestMapping("/api/v1/seller/orders")
class SellerOrderController(
    private val orderQueryService: OrderQueryService,
    private val orderCancelService: OrderCancelService,
) {

    @GetMapping
    fun list(
        @AuthenticatedSeller(requireManage = false) seller: SellerPrincipal,
        @RequestParam(required = false) status: SellerOrderStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<SellerOrderSummary>>> =
        ApiResponse.ok(PageResponse.from(orderQueryService.listForSeller(seller.sellerId, status, page, size)))

    @GetMapping("/{sellerOrderId}")
    fun get(
        @AuthenticatedSeller(requireManage = false) seller: SellerPrincipal,
        @PathVariable sellerOrderId: Long,
    ): ResponseEntity<ApiResponse<SellerOrderDetail>> =
        ApiResponse.ok(orderQueryService.getForSeller(seller.sellerId, sellerOrderId))

    /** `Idempotency-Key` 헤더는 필터가 검증·처리한다(IdempotencyProperties.paths). */
    @PostMapping("/{sellerOrderId}/items/{orderItemId}/cancel")
    fun cancelItem(
        @AuthenticatedSeller seller: SellerPrincipal,
        @PathVariable sellerOrderId: Long,
        @PathVariable orderItemId: Long,
        @Valid @RequestBody request: CancelItemRequest,
    ): ResponseEntity<ApiResponse<CancelItemResponse>> = ApiResponse.ok(
        CancelItemResponse.from(orderCancelService.cancelBySeller(seller.sellerId, sellerOrderId, orderItemId, request.reason)),
    )
}
