package com.dogyun.kairos.settlement.infrastructure

import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.settlement.domain.Settlement
import com.dogyun.kairos.settlement.domain.SettlementLine
import com.dogyun.kairos.settlement.domain.SettlementPeriod
import com.dogyun.kairos.settlement.domain.SettlementRepository
import com.dogyun.kairos.settlement.domain.SettlementStatus
import com.dogyun.kairos.settlement.domain.LineCalculation
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 새 정산만 INSERT 한다(`saveAndFlush`: UNIQUE 위반이 호출 지점에서 바로 `DataIntegrityViolationException` 으로 올라온다).
 * 기존 정산은 상태·합계 열만 바꾸고 라인은 건드리지 않는다(생성 뒤 불변).
 * 목록 조회는 라인 없이 헤더만 복원한다(`lineCount` 는 헤더에 있다). 라인은 [findById]·[findLines]로 읽는다.
 */
@Repository
@Transactional(readOnly = true)
class SettlementRepositoryImpl(
    private val settlementJpaRepository: SettlementJpaRepository,
    private val lineJpaRepository: SettlementLineJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : SettlementRepository {

    @Transactional
    override fun save(s: Settlement): Settlement {
        val saved = if (s.id == null) {
            val entity = SettlementEntity(
                s.sellerId, s.period.start, s.period.end, s.period.payoutDate, s.status,
                s.salesAmount, s.sellerCouponDiscount, s.commissionAmount, s.shippingFeeOwed, s.returnFeeBorne,
                s.adjustmentAmount, s.totalAmount, s.payableAmount, s.lineCount, s.confirmedAt, s.paidAt, s.paidMemo,
            )
            s.lines.forEach {
                val c = it.calc
                entity.lines.add(
                    SettlementLineEntity(
                        entity, it.sellerOrderId, it.orderId, it.orderNo, it.confirmedAt, c.salesAmount, c.sellerCouponDiscount,
                        c.commissionBase, c.commissionRateBp, c.commissionAmount, c.shippingFeeOwed, c.returnFeeBorne, c.lineAmount,
                    ),
                )
            }
            settlementJpaRepository.saveAndFlush(entity)
        } else {
            val entity = settlementJpaRepository.findById(requireNotNull(s.id)).orElseThrow { IllegalStateException("정산이 없다: ${s.id}") }
            entity.change(s.status, s.adjustmentAmount, s.totalAmount, s.payableAmount, s.confirmedAt, s.paidAt, s.paidMemo)
            settlementJpaRepository.saveAndFlush(entity)
        }
        return toDomain(saved, findLines(saved.id))
    }

    override fun findById(id: Long): Settlement? =
        settlementJpaRepository.findById(id).map { toDomain(it, findLines(id)) }.orElse(null)

    @Transactional
    override fun findByIdForUpdate(id: Long): Settlement? =
        settlementJpaRepository.findByIdForUpdate(id)?.let { toDomain(it, findLines(id)) }

    override fun existsBySellerIdAndPeriodStart(sellerId: Long, periodStart: LocalDate): Boolean =
        settlementJpaRepository.existsBySellerIdAndPeriodStart(sellerId, periodStart)

    override fun countByPeriodStart(periodStart: LocalDate): Int = settlementJpaRepository.countByPeriodStart(periodStart).toInt()

    override fun findSettledSellerOrderIds(sellerOrderIds: Collection<Long>): Set<Long> =
        if (sellerOrderIds.isEmpty()) emptySet() else lineJpaRepository.findSellerOrderIdsIn(sellerOrderIds).toSet()

    override fun findPageBySellerId(sellerId: Long, page: Int, size: Int): PageResult<Settlement> {
        val result = settlementJpaRepository.findBySellerId(
            sellerId, PageRequest.of(page, size, Sort.by(Sort.Order.desc("periodStart"), Sort.Order.desc("id"))),
        )
        return PageResult.of(result.content.map { toDomain(it, emptyList()) }, page, size, result.totalElements)
    }

    override fun findPageForAdmin(sellerId: Long?, status: SettlementStatus?, page: Int, size: Int): PageResult<Settlement> {
        val s = QSettlementEntity.settlementEntity
        val where = com.querydsl.core.BooleanBuilder()
        sellerId?.let { where.and(s.sellerId.eq(it)) }
        status?.let { where.and(s.settlementStatus.eq(it)) }
        val content = queryFactory.selectFrom(s).where(where)
            .orderBy(s.periodStart.desc(), s.id.desc())
            .offset(page.toLong() * size).limit(size.toLong())
            .fetch()
        val total = requireNotNull(queryFactory.select(s.count()).from(s).where(where).fetchOne())
        return PageResult.of(content.map { toDomain(it, emptyList()) }, page, size, total)
    }

    override fun findLines(settlementId: Long): List<SettlementLine> =
        lineJpaRepository.findAllBySettlementIdOrderBySellerOrderIdAsc(settlementId).map {
            SettlementLine(
                it.id, it.sellerOrderId, it.orderId, it.orderNo, it.confirmedAt,
                LineCalculation(
                    it.salesAmount, it.sellerCouponDiscount, it.commissionBase, it.commissionRateBp,
                    it.commissionAmount, it.shippingFeeOwed, it.returnFeeBorne, it.lineAmount,
                ),
            )
        }

    private fun toDomain(e: SettlementEntity, lines: List<SettlementLine>) = Settlement(
        id = e.id,
        sellerId = e.sellerId,
        period = SettlementPeriod.of(e.periodStart),
        status = e.settlementStatus,
        lines = lines,
        salesAmount = e.salesAmount,
        sellerCouponDiscount = e.sellerCouponDiscount,
        commissionAmount = e.commissionAmount,
        shippingFeeOwed = e.shippingFeeOwed,
        returnFeeBorne = e.returnFeeBorne,
        adjustmentAmount = e.adjustmentAmount,
        totalAmount = e.totalAmount,
        payableAmount = e.payableAmount,
        lineCount = e.lineCount,
        confirmedAt = e.confirmedAt,
        paidAt = e.paidAt,
        paidMemo = e.paidMemo,
    )
}
