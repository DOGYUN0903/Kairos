package com.dogyun.kairos.settlement.presentation

import com.dogyun.kairos.settlement.application.GenerateSettlementCommand
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class GenerateSettlementRequest(@field:NotNull val periodStart: LocalDate?) {
    fun toCommand() = GenerateSettlementCommand(requireNotNull(periodStart))
}
