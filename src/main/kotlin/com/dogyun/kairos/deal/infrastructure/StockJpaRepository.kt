package com.dogyun.kairos.deal.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface StockJpaRepository : JpaRepository<StockEntity, Long> {
    fun findByDealId(dealId: Long): StockEntity?
}
