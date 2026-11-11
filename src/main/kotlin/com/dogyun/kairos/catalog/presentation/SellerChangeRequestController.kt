package com.dogyun.kairos.catalog.presentation

import com.dogyun.kairos.catalog.application.ChangeRequestService
import com.dogyun.kairos.catalog.application.SellerChangeRequestDetail
import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.security.AuthenticatedSeller
import com.dogyun.kairos.common.security.SellerPrincipal
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 판매 중인 상품의 내용·정가 변경 요청. 제출·철회는 APPROVED 판매자만, 조회는 정지된 판매자도. */
@RestController
@RequestMapping("/api/v1/seller/products/{productId}/change-requests")
class SellerChangeRequestController(private val changeRequestService: ChangeRequestService) {

    @PostMapping
    fun submit(
        @AuthenticatedSeller seller: SellerPrincipal,
        @PathVariable productId: Long,
        @Valid @RequestBody request: SubmitChangeRequestRequest,
    ): ResponseEntity<ApiResponse<SubmitChangeRequestResponse>> =
        ApiResponse.created(SubmitChangeRequestResponse(changeRequestService.submit(seller, productId, request.toCommand())))

    @GetMapping("/{changeRequestId}")
    fun get(
        @AuthenticatedSeller(requireManage = false) seller: SellerPrincipal,
        @PathVariable productId: Long,
        @PathVariable changeRequestId: Long,
    ): ResponseEntity<ApiResponse<SellerChangeRequestDetail>> =
        ApiResponse.ok(changeRequestService.getMine(seller, productId, changeRequestId))

    @DeleteMapping("/{changeRequestId}")
    fun withdraw(
        @AuthenticatedSeller seller: SellerPrincipal,
        @PathVariable productId: Long,
        @PathVariable changeRequestId: Long,
    ): ResponseEntity<Void> {
        changeRequestService.withdraw(seller, productId, changeRequestId)
        return ApiResponse.noContent()
    }
}
