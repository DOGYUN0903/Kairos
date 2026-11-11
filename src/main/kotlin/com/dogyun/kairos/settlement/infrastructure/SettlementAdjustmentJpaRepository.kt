package com.dogyun.kairos.settlement.infrastructure

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

interface SettlementAdjustmentJpaRepository : JpaRepository<SettlementAdjustmentEntity, Long> {

    /**
     * 조건부 UPDATE 한 문장이 "한 번만, 한 정산에만 붙는다"를 지킨다: 같은 행을 동시에 붙이려는 두 트랜잭션 중
     * 뒤의 것은 `settlement_id IS NULL` 이 거짓이라 0행이다. 네이티브라 `@SQLRestriction` 이 없어 `status` 를 직접 건다.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = "UPDATE settlement_adjustment SET settlement_id = :settlementId " +
            "WHERE seller_id = :sellerId AND settlement_id IS NULL AND status = 'ACTIVE' " +
            "AND (adjustment_type = 'CARRY_OVER' OR created_at < :createdBefore)",
        nativeQuery = true,
    )
    fun attach(
        @Param("sellerId") sellerId: Long,
        @Param("settlementId") settlementId: Long,
        @Param("createdBefore") createdBefore: LocalDateTime,
    ): Int

    /** [attach] 와 같은 행 잠금으로 직렬화되는 조건부 소프트 삭제. CARRY_OVER 는 지우지 않는다(source UNIQUE 가 삭제 행도 센다). */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = "UPDATE settlement_adjustment SET status = 'DELETED', modified_at = :now " +
            "WHERE id = :id AND settlement_id IS NULL AND status = 'ACTIVE' AND adjustment_type = 'MANUAL'",
        nativeQuery = true,
    )
    fun softDeleteUnattachedManual(@Param("id") id: Long, @Param("now") now: LocalDateTime): Int

    @Query(
        "select distinct a.sellerId from SettlementAdjustmentEntity a where a.settlementId is null " +
            "and a.adjustmentType = com.dogyun.kairos.settlement.domain.AdjustmentType.MANUAL and a.createdAt < :createdBefore",
    )
    fun findSellerIdsWithUnattached(@Param("createdBefore") createdBefore: LocalDateTime): List<Long>

    fun findAllBySettlementIdOrderByIdAsc(settlementId: Long): List<SettlementAdjustmentEntity>
    fun findBySellerId(sellerId: Long, pageable: Pageable): Page<SettlementAdjustmentEntity>
}
