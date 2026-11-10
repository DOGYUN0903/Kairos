package com.dogyun.kairos.settlement.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.settlement.application.GenerateSettlementResult
import com.dogyun.kairos.settlement.application.SettlementGenerationService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 관리자 정산 API(admin 경로는 ADMIN 권한만 접근한다). */
@RestController
@RequestMapping("/api/v1/admin/settlements")
class AdminSettlementController(private val generationService: SettlementGenerationService) {

    @PostMapping("/generate")
    fun generate(@Valid @RequestBody request: GenerateSettlementRequest): ResponseEntity<ApiResponse<GenerateSettlementResult>> =
        ApiResponse.ok(generationService.generate(request.toCommand().periodStart))
}
