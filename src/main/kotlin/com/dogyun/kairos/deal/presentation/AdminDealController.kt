package com.dogyun.kairos.deal.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.deal.application.DealService
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
@RequestMapping("/api/v1/admin/deals")
class AdminDealController(private val dealService: DealService) {

    @PostMapping
    fun create(@Valid @RequestBody request: DealRequest): ResponseEntity<ApiResponse<CreateDealResponse>> =
        ApiResponse.created(CreateDealResponse(dealService.create(request.toCreateCommand())))

    @PutMapping("/{dealId}")
    fun update(
        @PathVariable dealId: Long,
        @Valid @RequestBody request: DealRequest,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        dealService.update(dealId, request.toUpdateCommand())
        return ApiResponse.ok(null)
    }

    @DeleteMapping("/{dealId}")
    fun delete(@PathVariable dealId: Long): ResponseEntity<Void> {
        dealService.delete(dealId)
        return ApiResponse.noContent()
    }
}
