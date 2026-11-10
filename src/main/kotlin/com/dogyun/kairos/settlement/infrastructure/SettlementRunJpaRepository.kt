package com.dogyun.kairos.settlement.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface SettlementRunJpaRepository : JpaRepository<SettlementRunEntity, Long> {
    fun existsByPeriodStart(periodStart: LocalDate): Boolean

    @Query("select r.periodStart from SettlementRunEntity r")
    fun findAllPeriodStarts(): List<LocalDate>
}
