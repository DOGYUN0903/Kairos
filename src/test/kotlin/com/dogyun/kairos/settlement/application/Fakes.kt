package com.dogyun.kairos.settlement.application

import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.settlement.domain.AdjustmentType
import com.dogyun.kairos.settlement.domain.Settlement
import com.dogyun.kairos.settlement.domain.SettlementAdjustment
import com.dogyun.kairos.settlement.domain.SettlementAdjustmentRepository
import com.dogyun.kairos.settlement.domain.SettlementLine
import com.dogyun.kairos.settlement.domain.SettlementRepository
import com.dogyun.kairos.settlement.domain.SettlementRun
import com.dogyun.kairos.settlement.domain.SettlementRunRepository
import com.dogyun.kairos.settlement.domain.SettlementSource
import com.dogyun.kairos.settlement.domain.SettlementStatus
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDate
import java.time.LocalDateTime

class FakeConfirmedSellerOrderReader(val sources: MutableList<SettlementSource> = mutableListOf()) : ConfirmedSellerOrderReader {
    override fun findConfirmedBetween(from: LocalDateTime, to: LocalDateTime, afterId: Long, limit: Int): List<SettlementSource> =
        sources.filter { it.confirmedAt >= from && it.confirmedAt < to && it.sellerOrderId > afterId }
            .sortedBy { it.sellerOrderId }.take(limit)
}

/** `(seller_id, period_start)`·`settlement_line.seller_order_id` UNIQUE 를 [DataIntegrityViolationException] 으로 흉내 낸다. */
class FakeSettlementRepository : SettlementRepository {
    val rows = LinkedHashMap<Long, Settlement>()
    private var seq = 0L
    private val failSellers = mutableSetOf<Long>()

    /** 이 판매자의 새 정산 저장에서 예외를 던진다. */
    fun failOnSave(sellerId: Long) { failSellers += sellerId }
    private val diveSellers = mutableSetOf<Long>()

    /** 이 판매자의 새 정산 저장에서 (seller, period) 가 아닌 UNIQUE 위반(예: 라인 seller_order_id)을 흉내 낸다. */
    fun lineCollisionOnSave(sellerId: Long) { diveSellers += sellerId }
    fun clearFailures() { failSellers.clear(); diveSellers.clear() }

    override fun save(s: Settlement): Settlement {
        if (s.id == null) {
            if (s.sellerId in diveSellers) throw DataIntegrityViolationException("uk_settlement_line_seller_order")
            if (s.sellerId in failSellers) throw IllegalStateException("테스트 실패 주입: ${s.sellerId}")
            if (rows.values.any { it.sellerId == s.sellerId && it.period == s.period }) throw DataIntegrityViolationException("uk_settlement_seller_period")
            val lineIds = rows.values.flatMap { r -> r.lines.map { it.sellerOrderId } }.toSet()
            if (s.lines.any { it.sellerOrderId in lineIds }) throw DataIntegrityViolationException("uk_settlement_line_seller_order")
        }
        val id = s.id ?: ++seq
        val copy = Settlement(
            id, s.sellerId, s.period, s.status,
            s.lines.map { SettlementLine(it.id ?: (id * 1000 + it.sellerOrderId), it.sellerOrderId, it.orderId, it.orderNo, it.confirmedAt, it.calc) },
            s.salesAmount, s.sellerCouponDiscount, s.commissionAmount, s.shippingFeeOwed, s.returnFeeBorne,
            s.adjustmentAmount, s.totalAmount, s.payableAmount, s.lineCount, s.confirmedAt, s.paidAt, s.paidMemo,
        )
        // 기존 행은 라인을 바꾸지 않는다(생성 뒤 불변).
        val stored = rows[id]?.let { old ->
            Settlement(
                id, old.sellerId, old.period, copy.status, old.lines, old.salesAmount, old.sellerCouponDiscount, old.commissionAmount,
                old.shippingFeeOwed, old.returnFeeBorne, copy.adjustmentAmount, copy.totalAmount, copy.payableAmount, old.lineCount,
                copy.confirmedAt, copy.paidAt, copy.paidMemo,
            )
        } ?: copy
        rows[id] = stored
        return copyOf(stored)
    }

    private fun copyOf(s: Settlement) = Settlement(
        s.id, s.sellerId, s.period, s.status, s.lines, s.salesAmount, s.sellerCouponDiscount, s.commissionAmount, s.shippingFeeOwed,
        s.returnFeeBorne, s.adjustmentAmount, s.totalAmount, s.payableAmount, s.lineCount, s.confirmedAt, s.paidAt, s.paidMemo,
    )

    override fun findById(id: Long): Settlement? = rows[id]?.let { copyOf(it) }
    override fun findByIdForUpdate(id: Long): Settlement? = findById(id)
    override fun existsBySellerIdAndPeriodStart(sellerId: Long, periodStart: LocalDate) =
        rows.values.any { it.sellerId == sellerId && it.period.start == periodStart }
    override fun countByPeriodStart(periodStart: LocalDate) = rows.values.count { it.period.start == periodStart }
    override fun findSettledSellerOrderIds(sellerOrderIds: Collection<Long>): Set<Long> =
        rows.values.flatMap { r -> r.lines.map { it.sellerOrderId } }.filter { it in sellerOrderIds }.toSet()
    override fun findPageBySellerId(sellerId: Long, page: Int, size: Int): PageResult<Settlement> = throw UnsupportedOperationException()
    override fun findPageForAdmin(sellerId: Long?, status: SettlementStatus?, page: Int, size: Int): PageResult<Settlement> =
        throw UnsupportedOperationException()
    override fun findLines(settlementId: Long): List<SettlementLine> = rows[settlementId]?.lines.orEmpty()

    fun bySeller(sellerId: Long, periodStart: LocalDate): Settlement? =
        rows.values.firstOrNull { it.sellerId == sellerId && it.period.start == periodStart }
}

/** `created_at` 은 [now] 로 채운다(저장 시 주어지면 그 값). */
class FakeSettlementAdjustmentRepository(var now: () -> LocalDateTime) : SettlementAdjustmentRepository {
    val rows = LinkedHashMap<Long, SettlementAdjustment>()
    private val deleted = mutableSetOf<Long>()
    private var seq = 0L

    override fun save(a: SettlementAdjustment): SettlementAdjustment {
        if (a.id == null && a.sourceSettlementId != null && rows.values.any { it.sourceSettlementId == a.sourceSettlementId }) {
            throw DataIntegrityViolationException("uk_settlement_adjustment_source")
        }
        val id = a.id ?: ++seq
        val saved = SettlementAdjustment(
            id, a.sellerId, a.settlementId, a.type, a.amount, a.reason, a.refSellerOrderId, a.sourceSettlementId,
            a.createdByAdminId, a.createdAt ?: now(),
        )
        rows[id] = saved
        return saved
    }

    override fun findById(id: Long): SettlementAdjustment? = rows[id]?.takeIf { id !in deleted }

    private fun attachable(a: SettlementAdjustment, createdBefore: LocalDateTime) =
        a.id !in deleted && a.settlementId == null && (a.type == AdjustmentType.CARRY_OVER || a.createdAt!! < createdBefore)

    override fun attach(sellerId: Long, settlementId: Long, createdBefore: LocalDateTime): Int {
        val targets = rows.values.filter { it.sellerId == sellerId && attachable(it, createdBefore) }
        targets.forEach {
            rows[it.id!!] = SettlementAdjustment(
                it.id, it.sellerId, settlementId, it.type, it.amount, it.reason, it.refSellerOrderId, it.sourceSettlementId,
                it.createdByAdminId, it.createdAt,
            )
        }
        return targets.size
    }

    override fun findBySettlementId(settlementId: Long) = rows.values.filter { it.settlementId == settlementId && it.id !in deleted }
    override fun findSellerIdsWithUnattached(createdBefore: LocalDateTime): Set<Long> =
        rows.values.filter { attachable(it, createdBefore) }.map { it.sellerId }.toSet()
    override fun findPageBySellerId(sellerId: Long, page: Int, size: Int): PageResult<SettlementAdjustment> {
        val all = rows.values.filter { it.sellerId == sellerId && it.id !in deleted }.sortedByDescending { it.id }
        return PageResult.of(all.drop(page * size).take(size), page, size, all.size.toLong())
    }
    override fun delete(a: SettlementAdjustment) { deleted += a.id!! }
    override fun deleteUnattachedManual(id: Long): Boolean {
        val a = rows[id] ?: return false
        if (id in deleted || a.settlementId != null || a.type != AdjustmentType.MANUAL) return false
        deleted += id
        return true
    }
}

class FakeSettlementRunRepository : SettlementRunRepository {
    /** 저장 순서 그대로. */
    val runs = mutableListOf<SettlementRun>()

    override fun save(run: SettlementRun): SettlementRun {
        if (runs.any { it.periodStart == run.periodStart }) throw DataIntegrityViolationException("uk_settlement_run_period")
        runs += run
        return run
    }

    override fun exists(periodStart: LocalDate) = runs.any { it.periodStart == periodStart }
    override fun findAllPeriodStarts() = runs.map { it.periodStart }.toSet()
}

class FakeSettlementSellerReader(vararg sellers: SettlementSellerInfo) : SettlementSellerReader {
    private val byId = sellers.associateBy { it.sellerId }
    override fun findSellers(sellerIds: Collection<Long>) = byId.filterKeys { it in sellerIds }
}
