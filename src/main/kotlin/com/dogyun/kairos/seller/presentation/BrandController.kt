package com.dogyun.kairos.seller.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.seller.application.BrandPublic
import com.dogyun.kairos.seller.application.BrandQueryService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 비로그인 공개 브랜드 페이지. */
@RestController
@RequestMapping("/api/v1/brands")
class BrandController(private val brandQueryService: BrandQueryService) {

    @GetMapping("/{brandId}")
    fun get(@PathVariable brandId: Long): ResponseEntity<ApiResponse<BrandPublic>> =
        ApiResponse.ok(brandQueryService.getPublic(brandId))
}
