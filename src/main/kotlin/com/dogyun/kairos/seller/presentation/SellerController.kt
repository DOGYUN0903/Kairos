package com.dogyun.kairos.seller.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.security.LoginMember
import com.dogyun.kairos.seller.application.SellerDetail
import com.dogyun.kairos.seller.application.SellerQueryService
import com.dogyun.kairos.seller.application.SellerService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 신청·내 정보는 아직 판매자가 아닐 수 있는 회원이 호출하므로 @AuthenticatedSeller 를 쓰지 않는다. */
@RestController
@RequestMapping("/api/v1/seller")
class SellerController(
    private val sellerService: SellerService,
    private val sellerQueryService: SellerQueryService,
) {

    @PostMapping("/application")
    fun apply(
        @AuthenticationPrincipal loginMember: LoginMember,
        @Valid @RequestBody request: SellerApplicationRequest,
    ): ResponseEntity<ApiResponse<ApplySellerResponse>> =
        ApiResponse.created(ApplySellerResponse(sellerService.apply(loginMember.id, request.toCommand())))

    @PutMapping("/application")
    fun updateApplication(
        @AuthenticationPrincipal loginMember: LoginMember,
        @Valid @RequestBody request: SellerApplicationRequest,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        sellerService.updateApplication(loginMember.id, request.toCommand())
        return ApiResponse.ok(null)
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal loginMember: LoginMember): ResponseEntity<ApiResponse<SellerDetail>> =
        ApiResponse.ok(sellerQueryService.getMine(loginMember.id))
}
