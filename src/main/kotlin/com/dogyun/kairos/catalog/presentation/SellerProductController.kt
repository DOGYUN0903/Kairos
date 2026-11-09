package com.dogyun.kairos.catalog.presentation

import com.dogyun.kairos.catalog.application.ProductQueryService
import com.dogyun.kairos.catalog.application.ProductService
import com.dogyun.kairos.catalog.application.SellerProductDetail
import com.dogyun.kairos.catalog.application.SellerProductSummary
import com.dogyun.kairos.catalog.domain.ProductStatus
import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.presentation.PageResponse
import com.dogyun.kairos.common.security.AuthenticatedSeller
import com.dogyun.kairos.common.security.SellerPrincipal
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 조회·중지·종료는 정지된 판매자도 할 수 있고, 등록·수정·가격·SKU·재개는 APPROVED 판매자만 할 수 있다. */
@RestController
@RequestMapping("/api/v1/seller/products")
class SellerProductController(
    private val productService: ProductService,
    private val productQueryService: ProductQueryService,
) {

    @PostMapping
    fun register(
        @AuthenticatedSeller seller: SellerPrincipal,
        @Valid @RequestBody request: RegisterProductRequest,
    ): ResponseEntity<ApiResponse<RegisterProductResponse>> =
        ApiResponse.created(RegisterProductResponse(productService.register(seller, request.toCommand())))

    @GetMapping
    fun listMine(
        @AuthenticatedSeller(requireManage = false) seller: SellerPrincipal,
        @RequestParam(required = false) status: ProductStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<SellerProductSummary>>> =
        ApiResponse.ok(PageResponse.from(productQueryService.listMine(seller, status, page, size)))

    @GetMapping("/{productId}")
    fun getMine(
        @AuthenticatedSeller(requireManage = false) seller: SellerPrincipal,
        @PathVariable productId: Long,
    ): ResponseEntity<ApiResponse<SellerProductDetail>> = ApiResponse.ok(productQueryService.getMine(seller, productId))

    @PutMapping("/{productId}")
    fun editContent(
        @AuthenticatedSeller seller: SellerPrincipal,
        @PathVariable productId: Long,
        @Valid @RequestBody request: ProductContentRequest,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        productService.editContent(seller, productId, request.toCommand())
        return ApiResponse.ok(null)
    }

    @PatchMapping("/{productId}/price")
    fun changeSalePrice(
        @AuthenticatedSeller seller: SellerPrincipal,
        @PathVariable productId: Long,
        @Valid @RequestBody request: ChangeSalePriceRequest,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        productService.changeSalePrice(seller, productId, request.toCommand())
        return ApiResponse.ok(null)
    }

    @PatchMapping("/{productId}/skus/{skuId}")
    fun updateSku(
        @AuthenticatedSeller seller: SellerPrincipal,
        @PathVariable productId: Long,
        @PathVariable skuId: Long,
        @Valid @RequestBody request: UpdateSkuRequest,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        productService.updateSku(seller, productId, skuId, request.toCommand())
        return ApiResponse.ok(null)
    }

    @PostMapping("/{productId}/pause")
    fun pause(
        @AuthenticatedSeller(requireManage = false) seller: SellerPrincipal,
        @PathVariable productId: Long,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        productService.pause(seller, productId)
        return ApiResponse.ok(null)
    }

    @PostMapping("/{productId}/resume")
    fun resume(
        @AuthenticatedSeller seller: SellerPrincipal,
        @PathVariable productId: Long,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        productService.resume(seller, productId)
        return ApiResponse.ok(null)
    }

    @PostMapping("/{productId}/end")
    fun end(
        @AuthenticatedSeller(requireManage = false) seller: SellerPrincipal,
        @PathVariable productId: Long,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        productService.end(seller, productId)
        return ApiResponse.ok(null)
    }
}
