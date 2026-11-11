package com.dogyun.kairos.settlement.domain

import com.dogyun.kairos.common.domain.PageResult
import java.time.LocalDateTime

interface SettlementAdjustmentRepository {
    fun save(a: SettlementAdjustment): SettlementAdjustment
    fun findById(id: Long): SettlementAdjustment?

    /**
     * `UPDATE ... SET settlement_id = ? WHERE seller_id = ? AND settlement_id IS NULL AND status = 'ACTIVE'
     * AND (adjustment_type = 'CARRY_OVER' OR created_at < ?)`. 붙은 행 수를 돌려준다.
     * CARRY_OVER는 created_at을 보지 않는다: 생성이 밀려 여러 기간을 따라잡을 때 앞 기간의 CARRY_OVER는
     * created_at이 뒤 기간 periodEnd보다 늦어 `created_at < ?`만으로는 다음 정산에 붙지 못하고 한 기간 건너뛴다.
     * 생성은 오래된 기간부터 고정이라 CARRY_OVER는 자기 source 정산 이후에만 생기므로 조건 없이 붙여도 앞 기간에 붙을 수 없다.
     */
    fun attach(sellerId: Long, settlementId: Long, createdBefore: LocalDateTime): Int
    fun findBySettlementId(settlementId: Long): List<SettlementAdjustment>

    /** 정산을 만들게 하는 조정이 있는 판매자: 미부착 `MANUAL`(created_at < 기준). CARRY_OVER만 있는 판매자는 제외한다(다음 정산이 생길 때 [attach]가 가져간다). */
    fun findSellerIdsWithUnattached(createdBefore: LocalDateTime): Set<Long>
    fun findPageBySellerId(sellerId: Long, page: Int, size: Int): PageResult<SettlementAdjustment>

    /**
     * `UPDATE ... SET status = 'DELETED' WHERE id = ? AND settlement_id IS NULL AND status = 'ACTIVE' AND adjustment_type = 'MANUAL'`.
     * 정산에 붙이는 [attach]와 같은 행 잠금으로 직렬화돼, 붙은 조정은 절대 지워지지 않는다. 지웠으면 true.
     */
    fun deleteUnattachedManual(id: Long): Boolean
}
