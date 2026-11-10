package com.dogyun.kairos.benefit.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface PointLotUsageJpaRepository : JpaRepository<PointLotUsageEntity, Long> {
    fun findByTransactionIdOrderById(transactionId: Long): List<PointLotUsageEntity>
}
