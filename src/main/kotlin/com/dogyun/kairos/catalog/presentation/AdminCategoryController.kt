package com.dogyun.kairos.catalog.presentation

import com.dogyun.kairos.catalog.application.CategoryService
import com.dogyun.kairos.common.presentation.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/categories")
class AdminCategoryController(private val categoryService: CategoryService) {

    /** `Idempotency-Key` 헤더는 필터가 검증·처리한다(IdempotencyProperties.paths). */
    @PostMapping
    fun create(@Valid @RequestBody request: CategoryRequest): ResponseEntity<ApiResponse<CreateCategoryResponse>> =
        ApiResponse.created(CreateCategoryResponse(categoryService.create(request.toCreateCommand())))

    @PutMapping("/order")
    fun reorder(@Valid @RequestBody request: CategoryOrderRequest): ResponseEntity<ApiResponse<Nothing?>> {
        categoryService.reorder(request.parentId, request.categoryIds)
        return ApiResponse.ok(null)
    }

    @PutMapping("/{categoryId}")
    fun update(
        @PathVariable categoryId: Long,
        @Valid @RequestBody request: CategoryRequest,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        categoryService.update(categoryId, request.toUpdateCommand())
        return ApiResponse.ok(null)
    }

    @DeleteMapping("/{categoryId}")
    fun delete(@PathVariable categoryId: Long): ResponseEntity<Void> {
        categoryService.delete(categoryId)
        return ApiResponse.noContent()
    }
}
