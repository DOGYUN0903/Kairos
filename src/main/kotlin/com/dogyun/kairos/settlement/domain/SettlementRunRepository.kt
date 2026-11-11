package com.dogyun.kairos.settlement.domain

import java.time.LocalDate

interface SettlementRunRepository {
    fun save(run: SettlementRun): SettlementRun
    fun exists(periodStart: LocalDate): Boolean
    fun findAllPeriodStarts(): Set<LocalDate>
}
