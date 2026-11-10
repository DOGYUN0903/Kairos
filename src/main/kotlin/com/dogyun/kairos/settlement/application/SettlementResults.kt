package com.dogyun.kairos.settlement.application

import java.time.LocalDate

data class GenerateSettlementResult(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val sellerCount: Int,
    val createdCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val alreadyGenerated: Boolean,
)
