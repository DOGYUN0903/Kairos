package com.dogyun.kairos.deal.presentation

import com.dogyun.kairos.common.presentation.KstTime
import com.dogyun.kairos.deal.application.CreateDealCommand
import com.dogyun.kairos.deal.application.UpdateDealCommand
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.OffsetDateTime

data class DealRequest(
    @field:NotBlank val productName: String,
    @field:Min(1) val originalPrice: Long,
    @field:Min(1) val dealPrice: Long,
    @field:Min(1) val totalQuantity: Int,
    @field:Min(1) val perMemberLimit: Int,
    val openAt: OffsetDateTime,
    val closeAt: OffsetDateTime,
) {
    fun toCreateCommand() =
        CreateDealCommand(productName, originalPrice, dealPrice, totalQuantity, perMemberLimit, KstTime.toLocal(openAt), KstTime.toLocal(closeAt))

    fun toUpdateCommand() =
        UpdateDealCommand(productName, originalPrice, dealPrice, totalQuantity, perMemberLimit, KstTime.toLocal(openAt), KstTime.toLocal(closeAt))
}

data class CreateDealResponse(val dealId: Long)
