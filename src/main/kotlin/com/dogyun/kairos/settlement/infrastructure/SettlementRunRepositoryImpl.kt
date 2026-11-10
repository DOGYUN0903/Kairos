package com.dogyun.kairos.settlement.infrastructure

import com.dogyun.kairos.settlement.domain.SettlementRun
import com.dogyun.kairos.settlement.domain.SettlementRunRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class SettlementRunRepositoryImpl(private val jpaRepository: SettlementRunJpaRepository) : SettlementRunRepository {

    /** `saveAndFlush`: `period_start` UNIQUE 위반(동시 생성 완료 기록)이 즉시 `DataIntegrityViolationException` 으로 올라온다. */
    override fun save(run: SettlementRun): SettlementRun {
        jpaRepository.saveAndFlush(SettlementRunEntity(run.periodStart, run.generatedAt, run.sellerCount))
        return run
    }

    override fun exists(periodStart: LocalDate): Boolean = jpaRepository.existsByPeriodStart(periodStart)

    override fun findAllPeriodStarts(): Set<LocalDate> = jpaRepository.findAllPeriodStarts().toSet()
}
