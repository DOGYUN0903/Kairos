package com.dogyun.kairos.settlement.application

import com.dogyun.kairos.common.application.TransactionRunner
import com.dogyun.kairos.common.application.sweepByKeyset
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.settlement.domain.Settlement
import com.dogyun.kairos.settlement.domain.SettlementAdjustment
import com.dogyun.kairos.settlement.domain.SettlementAdjustmentRepository
import com.dogyun.kairos.settlement.domain.SettlementPeriod
import com.dogyun.kairos.settlement.domain.SettlementRepository
import com.dogyun.kairos.settlement.domain.SettlementRun
import com.dogyun.kairos.settlement.domain.SettlementRunRepository
import com.dogyun.kairos.settlement.domain.SettlementSource
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 주간 정산 생성. 감싸는 트랜잭션이 없고 판매자마다 트랜잭션 하나다(정산 INSERT → 조정 붙이기 → 합계 반영 → CARRY_OVER).
 * 몇 번을 돌려도, 중간에 멈춘 뒤 다시 돌려도 결과가 같다:
 * - 판매자 주문은 `settlement_line.seller_order_id` UNIQUE 로, 정산은 `(seller_id, period_start)` UNIQUE 로 한 번만 생긴다.
 * - 이미 라인이 있는 판매자 주문은 읽을 때 빼고, 이미 정산이 있는 판매자는 건너뛴다.
 * - 모든 판매자가 끝났을 때만 `settlement_run` 을 남긴다. 기간은 오래된 것부터만 만든다.
 */
@Service
class SettlementGenerationService(
    private val sellerOrderReader: ConfirmedSellerOrderReader,
    private val settlementRepository: SettlementRepository,
    private val adjustmentRepository: SettlementAdjustmentRepository,
    private val runRepository: SettlementRunRepository,
    private val properties: SettlementProperties,
    private val tx: TransactionRunner,
    private val clock: Clock,
) {

    fun generate(periodStart: LocalDate): GenerateSettlementResult {
        val period = SettlementPeriod.of(periodStart)
        val now = LocalDateTime.now(clock)
        val startDate = properties.startDate
        if (period.endAt.isAfter(now) || periodStart.isBefore(startDate) ||
            (periodStart.isAfter(startDate) && !runRepository.exists(periodStart.minusDays(7)))
        ) {
            throw DomainException(ErrorCode.SETTLEMENT_PERIOD_INVALID)
        }
        if (runRepository.exists(periodStart)) return result(period, 0, 0, 0, 0, alreadyGenerated = true)

        // 기간 안 확정분 + 기간 시작 전 lookback 안(start-date 이후)에 확정됐지만 아직 어느 정산에도 없는 늦은 확정분.
        val lookbackFrom = period.startAt.minus(properties.lateConfirmLookback)
        val from = maxOf(lookbackFrom, startDate.atStartOfDay())
        val read = mutableListOf<SettlementSource>()
        sweepByKeyset(PAGE, { it.sellerOrderId }, { after -> sellerOrderReader.findConfirmedBetween(from, period.endAt, after, PAGE) }) {
            read.add(it)
        }
        val settled = read.chunked(PAGE).flatMap { chunk -> settlementRepository.findSettledSellerOrderIds(chunk.map { it.sellerOrderId }) }.toSet()
        val bySeller = read.filter { it.sellerOrderId !in settled }.groupBy { it.sellerId }
        val sellerIds = (bySeller.keys + adjustmentRepository.findSellerIdsWithUnattached(period.endAt)).sorted()

        var created = 0
        var skipped = 0
        var failed = 0
        for (sellerId in sellerIds) {
            try {
                val done = tx.run { generateForSeller(sellerId, period, bySeller[sellerId].orEmpty()) }
                if (done) created++ else skipped++
            } catch (e: DataIntegrityViolationException) {
                // (seller_id, period_start) 경합에서 진 것만 "이미 끝남"이다. 라인 UNIQUE 등 다른 위반은 실패로 센다
                // (건너뛰면 run 이 남고 그 판매자 주문이 어느 정산에도 들어가지 못한다).
                if (tx.run { settlementRepository.existsBySellerIdAndPeriodStart(sellerId, period.start) }) {
                    skipped++
                } else {
                    failed++
                    log.error("정산 생성 중 제약 위반: periodStart={}, sellerId={}", periodStart, sellerId, e)
                }
            } catch (e: RuntimeException) {
                failed++
                log.error("정산 생성에 실패했다: periodStart={}, sellerId={}", periodStart, sellerId, e)
            }
        }
        if (failed > 0) return result(period, sellerIds.size, created, skipped, failed, alreadyGenerated = false)
        return try {
            // 판매자 수는 이 실행이 다룬 수가 아니라 기간의 정산 수다(중단 뒤 재실행해도 한 번에 끝난 것과 같다).
            tx.run { runRepository.save(SettlementRun(periodStart, now, settlementRepository.countByPeriodStart(periodStart))) }
            result(period, sellerIds.size, created, skipped, 0, alreadyGenerated = false)
        } catch (e: DataIntegrityViolationException) {
            result(period, sellerIds.size, created, skipped, 0, alreadyGenerated = true)
        }
    }

    /**
     * [properties] 의 start-date 부터 [untilInclusive] 까지 아직 완료 기록이 없는 기간을 오래된 순으로 만든다.
     * 한 기간이라도 실패한 판매자가 있으면 거기서 멈춘다(다음 기간은 직전 기간이 있어야 만든다). 완료한 기간 수를 돌려준다.
     */
    fun generateDue(untilInclusive: LocalDate = SettlementPeriod.lastEndedBefore(LocalDateTime.now(clock)).start): Int {
        val done = runRepository.findAllPeriodStarts()
        var count = 0
        var start = properties.startDate
        while (!start.isAfter(untilInclusive)) {
            if (start !in done) {
                val r = generate(start)
                if (r.failedCount > 0) return count
                if (!r.alreadyGenerated) count++
            }
            start = start.plusDays(7)
        }
        return count
    }

    /** 이미 이 기간 정산이 있으면 false(건너뜀). 트랜잭션 안에서 부른다. */
    private fun generateForSeller(sellerId: Long, period: SettlementPeriod, sources: List<SettlementSource>): Boolean {
        // 이미 이 기간 정산이 있으면 건너뛴다. 그 뒤 새로 잡힌 미정산 늦은 확정분은 다음 기간의 lookback 이 가져간다
        // (확정 시각이 lookback 창 안에 있는 동안만 유효하다).
        if (settlementRepository.existsBySellerIdAndPeriodStart(sellerId, period.start)) return false
        val saved = settlementRepository.save(Settlement.generate(sellerId, period, sources, adjustmentAmount = 0))
        val settlementId = requireNotNull(saved.id)
        adjustmentRepository.attach(sellerId, settlementId, period.endAt)
        // attach 는 영속성 컨텍스트를 비운다(clearAutomatically) — 그 뒤에 다시 읽는다.
        val attached = adjustmentRepository.findBySettlementId(settlementId)
        val settlement = requireNotNull(settlementRepository.findById(settlementId))
        settlement.applyAdjustments(attached.sumOf { it.amount })
        if (settlement.carryOverAmount < 0) {
            adjustmentRepository.save(SettlementAdjustment.carryOver(sellerId, settlement.carryOverAmount, settlementId))
        }
        settlementRepository.save(settlement)
        return true
    }

    private fun result(period: SettlementPeriod, sellers: Int, created: Int, skipped: Int, failed: Int, alreadyGenerated: Boolean) =
        GenerateSettlementResult(period.start, period.end, sellers, created, skipped, failed, alreadyGenerated)

    private companion object {
        const val PAGE = 200
        val log = LoggerFactory.getLogger(SettlementGenerationService::class.java)
    }
}
