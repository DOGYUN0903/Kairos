package com.dogyun.kairos.settlement.application

import java.time.LocalDate

data class GenerateSettlementCommand(val periodStart: LocalDate)

data class CreateAdjustmentCommand(val amount: Long, val reason: String, val refSellerOrderId: Long?)
