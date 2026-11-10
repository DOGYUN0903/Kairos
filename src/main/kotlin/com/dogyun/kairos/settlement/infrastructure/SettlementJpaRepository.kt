package com.dogyun.kairos.settlement.infrastructure

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface SettlementJpaRepository : JpaRepository<SettlementEntity, Long> {
    fun existsBySellerIdAndPeriodStart(sellerId: Long, periodStart: LocalDate): Boolean
    fun findBySellerId(sellerId: Long, pageable: Pageable): Page<SettlementEntity>
}
