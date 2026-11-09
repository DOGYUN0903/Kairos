package com.dogyun.kairos.catalog.presentation

import com.dogyun.kairos.catalog.application.CategoryNode
import com.dogyun.kairos.catalog.application.CategoryQueryService
import com.dogyun.kairos.common.presentation.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 비로그인 공개 카테고리 트리. */
@RestController
@RequestMapping("/api/v1/categories")
class CategoryController(private val categoryQueryService: CategoryQueryService) {

    @GetMapping
    fun tree(): ResponseEntity<ApiResponse<List<CategoryNode>>> = ApiResponse.ok(categoryQueryService.tree())
}
