package com.dogyun.kairos.settlement.infrastructure

import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.settlement.domain.SettlementAdjustment
import com.dogyun.kairos.settlement.domain.SettlementAdjustmentRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Repository
@Transactional(readOnly = true)
class SettlementAdjustmentRepositoryImpl(
    private val jpaRepository: SettlementAdjustmentJpaRepository,
) : SettlementAdjustmentRepository {

    /** 새 조정만 저장한다(`saveAndFlush`: `source_settlement_id` UNIQUE 위반이 즉시 올라온다). 정산에 붙이기는 [attach]뿐이다. */
    @Transactional
    override fun save(a: SettlementAdjustment): SettlementAdjustment {
        check(a.id == null) { "조정은 수정하지 않는다: ${a.id}" }
        check(a.settlementId == null) { "정산에 붙은 조정은 새로 저장할 수 없다" }
        return toDomain(
            jpaRepository.saveAndFlush(
                SettlementAdjustmentEntity(a.sellerId, null, a.type, a.amount, a.reason, a.refSellerOrderId, a.sourceSettlementId, a.createdByAdminId),
            ),
        )
    }

    override fun findById(id: Long): SettlementAdjustment? = jpaRepository.findById(id).map(::toDomain).orElse(null)

    @Transactional
    override fun attach(sellerId: Long, settlementId: Long, createdBefore: LocalDateTime): Int =
        jpaRepository.attach(sellerId, settlementId, createdBefore)

    override fun findBySettlementId(settlementId: Long): List<SettlementAdjustment> =
        jpaRepository.findAllBySettlementIdOrderByIdAsc(settlementId).map(::toDomain)

    override fun findSellerIdsWithUnattached(createdBefore: LocalDateTime): Set<Long> =
        jpaRepository.findSellerIdsWithUnattached(createdBefore).toSet()

    override fun findPageBySellerId(sellerId: Long, page: Int, size: Int): PageResult<SettlementAdjustment> {
        val result = jpaRepository.findBySellerId(sellerId, PageRequest.of(page, size, Sort.by(Sort.Order.desc("id"))))
        return PageResult.of(result.content.map(::toDomain), page, size, result.totalElements)
    }

    @Transactional
    override fun delete(a: SettlementAdjustment) {
        val entity = jpaRepository.findById(requireNotNull(a.id)).orElse(null) ?: return
        entity.softDelete()
        jpaRepository.saveAndFlush(entity)
    }

    @Transactional
    override fun deleteUnattachedManual(id: Long): Boolean = jpaRepository.softDeleteUnattachedManual(id, LocalDateTime.now()) == 1

    private fun toDomain(e: SettlementAdjustmentEntity) = SettlementAdjustment(
        e.id, e.sellerId, e.settlementId, e.adjustmentType, e.amount, e.reason,
        e.refSellerOrderId, e.sourceSettlementId, e.createdByAdminId, e.createdAt,
    )
}
