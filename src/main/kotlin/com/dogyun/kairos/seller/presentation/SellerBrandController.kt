package com.dogyun.kairos.seller.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.security.AuthenticatedSeller
import com.dogyun.kairos.common.security.SellerPrincipal
import com.dogyun.kairos.seller.application.BrandDetail
import com.dogyun.kairos.seller.application.BrandService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/seller/brands")
class SellerBrandController(private val brandService: BrandService) {

    @PostMapping
    fun apply(
        @AuthenticatedSeller seller: SellerPrincipal,
        @Valid @RequestBody request: BrandRequest,
    ): ResponseEntity<ApiResponse<ApplyBrandResponse>> =
        ApiResponse.created(ApplyBrandResponse(brandService.apply(seller, request.toCommand())))

    @PutMapping("/{brandId}")
    fun reapply(
        @AuthenticatedSeller seller: SellerPrincipal,
        @PathVariable brandId: Long,
        @Valid @RequestBody request: BrandRequest,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        brandService.reapply(seller, brandId, request.toCommand())
        return ApiResponse.ok(null)
    }

    /** 정지된 판매자도 자기 브랜드는 조회할 수 있다. */
    @GetMapping
    fun listMine(
        @AuthenticatedSeller(requireManage = false) seller: SellerPrincipal,
    ): ResponseEntity<ApiResponse<List<BrandDetail>>> = ApiResponse.ok(brandService.listMine(seller))
}
