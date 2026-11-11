package com.dogyun.kairos.settlement.infrastructure

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface SettlementJpaRepository : JpaRepository<SettlementEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SettlementEntity s where s.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): SettlementEntity?

    fun existsBySellerIdAndPeriodStart(sellerId: Long, periodStart: LocalDate): Boolean
    fun countByPeriodStart(periodStart: LocalDate): Long
    fun findBySellerId(sellerId: Long, pageable: Pageable): Page<SettlementEntity>
}
