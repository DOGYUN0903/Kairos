package com.dogyun.kairos.settlement.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SettlementLineJpaRepository : JpaRepository<SettlementLineEntity, Long> {
    fun findAllBySettlementIdOrderBySellerOrderIdAsc(settlementId: Long): List<SettlementLineEntity>

    @Query("select l.sellerOrderId from SettlementLineEntity l where l.sellerOrderId in :sellerOrderIds")
    fun findSellerOrderIdsIn(@Param("sellerOrderIds") sellerOrderIds: Collection<Long>): List<Long>
}
