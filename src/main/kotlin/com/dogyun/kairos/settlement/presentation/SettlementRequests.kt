package com.dogyun.kairos.settlement.presentation

import com.dogyun.kairos.settlement.application.CreateAdjustmentCommand
import com.dogyun.kairos.settlement.application.GenerateSettlementCommand
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class GenerateSettlementRequest(@field:NotNull val periodStart: LocalDate?) {
    fun toCommand() = GenerateSettlementCommand(requireNotNull(periodStart))
}

/** 금액 0·빈 사유·500자 초과는 도메인이 `SETTLEMENT_ADJUSTMENT_INVALID` 로 거절한다. */
data class CreateAdjustmentRequest(
    @field:NotNull val amount: Long?,
    @field:NotNull val reason: String?,
    val refSellerOrderId: Long? = null,
) {
    fun toCommand() = CreateAdjustmentCommand(requireNotNull(amount), requireNotNull(reason), refSellerOrderId)
}

data class PaySettlementRequest(val memo: String? = null)

data class CreateAdjustmentResponse(val adjustmentId: Long)
