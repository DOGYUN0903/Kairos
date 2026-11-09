package com.dogyun.kairos.seller.presentation

import com.dogyun.kairos.seller.application.SellerApplicationCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class SellerApplicationRequest(
    @field:NotBlank val companyName: String,
    @field:Pattern(regexp = "^\\d{3}-\\d{2}-\\d{5}$") val businessNumber: String,
    @field:NotBlank val representativeName: String,
    @field:NotBlank val phone: String,
    @field:NotBlank val bank: String,
    @field:NotBlank val accountNumber: String,
    @field:NotBlank val holder: String,
) {
    fun toCommand() = SellerApplicationCommand(
        companyName, businessNumber, representativeName, phone, bank, accountNumber, holder,
    )
}

data class ApplySellerResponse(val sellerId: Long)
