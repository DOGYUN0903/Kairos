package com.dogyun.kairos.settlement.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.presentation.PageResponse
import com.dogyun.kairos.common.security.AuthenticatedSeller
import com.dogyun.kairos.common.security.SellerPrincipal
import com.dogyun.kairos.settlement.application.SettlementDetail
import com.dogyun.kairos.settlement.application.SettlementQueryService
import com.dogyun.kairos.settlement.application.SettlementSummary
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 판매자 정산 조회 API. 정지된 판매자도 볼 수 있고(requireManage=false), 남의 정산은 404. */
@RestController
@RequestMapping("/api/v1/seller/settlements")
class SellerSettlementController(private val queryService: SettlementQueryService) {

    @GetMapping
    fun list(
        @AuthenticatedSeller(requireManage = false) seller: SellerPrincipal,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<SettlementSummary>>> =
        ApiResponse.ok(PageResponse.from(queryService.listForSeller(seller.sellerId, page, size)))

    @GetMapping("/{settlementId}")
    fun get(
        @AuthenticatedSeller(requireManage = false) seller: SellerPrincipal,
        @PathVariable settlementId: Long,
    ): ResponseEntity<ApiResponse<SettlementDetail>> = ApiResponse.ok(queryService.getForSeller(seller.sellerId, settlementId))
}
