package com.dogyun.kairos.seller.presentation

import com.dogyun.kairos.seller.application.BrandApplicationCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class BrandRequest(
    @field:NotBlank @field:Size(max = 50) val name: String,
    @field:Size(max = 100) val englishName: String? = null,
    val logoFileId: Long? = null,
    @field:Size(max = 1000) val description: String? = null,
) {
    fun toCommand() = BrandApplicationCommand(name, englishName, logoFileId, description)
}

data class ApplyBrandResponse(val brandId: Long)
