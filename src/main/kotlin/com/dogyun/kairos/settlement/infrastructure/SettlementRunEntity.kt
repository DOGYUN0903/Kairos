package com.dogyun.kairos.settlement.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime

/** 기간 하나의 정산 생성이 끝났다는 기록. `period_start` UNIQUE 가 동시 완료 기록을 막는다. */
@Entity
@Table(name = "settlement_run")
class SettlementRunEntity(
    @Column(name = "period_start", nullable = false, updatable = false)
    val periodStart: LocalDate,
    @Column(name = "generated_at", nullable = false, updatable = false)
    val generatedAt: LocalDateTime,
    @Column(name = "seller_count", nullable = false, updatable = false)
    val sellerCount: Int,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L
}
