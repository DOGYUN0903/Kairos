package com.dogyun.kairos.settlement.infrastructure

import com.dogyun.kairos.CommerceTestSupport
import com.dogyun.kairos.settlement.domain.AdjustmentType
import com.dogyun.kairos.settlement.domain.Settlement
import com.dogyun.kairos.settlement.domain.SettlementAdjustment
import com.dogyun.kairos.settlement.domain.SettlementAdjustmentRepository
import com.dogyun.kairos.settlement.domain.SettlementPeriod
import com.dogyun.kairos.settlement.domain.SettlementRepository
import com.dogyun.kairos.settlement.domain.SettlementRun
import com.dogyun.kairos.settlement.domain.SettlementRunRepository
import com.dogyun.kairos.settlement.domain.SettlementSource
import com.dogyun.kairos.settlement.domain.SettlementSourceItem
import com.dogyun.kairos.settlement.domain.SettlementStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettlementRepositoryImplTest : CommerceTestSupport() {

    @Autowired
    private lateinit var settlementRepository: SettlementRepository

    @Autowired
    private lateinit var adjustmentRepository: SettlementAdjustmentRepository

    @Autowired
    private lateinit var runRepository: SettlementRunRepository

    private val period = SettlementPeriod.of(LocalDate.of(2026, 11, 2))
    private val confirmedAt = LocalDateTime.of(2026, 11, 3, 10, 0)

    private fun source(sellerOrderId: Long, sellerId: Long = 1L) = SettlementSource(
        sellerOrderId = sellerOrderId, sellerId = sellerId, orderId = sellerOrderId * 10, orderNo = "ORD-$sellerOrderId",
        confirmedAt = confirmedAt, commissionRateBp = 1000, shippingFee = 3_000, buyerShippingFeeRefunded = 0,
        items = listOf(SettlementSourceItem(1, true, 10_000, 0, null, 0)),
    )

    private fun generate(sellerId: Long, period: SettlementPeriod, vararg sellerOrderIds: Long) =
        Settlement.generate(sellerId, period, sellerOrderIds.map { source(it, sellerId) }, 0)

    @Test
    fun 같은_판매자_기간을_두_번_저장하면_DataIntegrityViolationException() {
        settlementRepository.save(generate(1L, period, 1L))

        assertFailsWith<DataIntegrityViolationException> { settlementRepository.save(generate(1L, period, 2L)) }
        // 다른 판매자·다른 기간은 된다
        settlementRepository.save(generate(2L, period, 3L))
        settlementRepository.save(generate(1L, period.next(), 4L))
    }

    @Test
    fun 같은_seller_order_id_라인을_두_정산에_넣으면_DataIntegrityViolationException() {
        settlementRepository.save(generate(1L, period, 1L, 2L))

        assertFailsWith<DataIntegrityViolationException> { settlementRepository.save(generate(1L, period.next(), 2L)) }
    }

    @Test
    fun 저장한_정산은_라인과_함께_복원되고_기존_정산_저장은_상태와_합계만_바꾼다() {
        val saved = settlementRepository.save(generate(1L, period, 5L, 3L))
        val id = assertNotNull(saved.id)

        val found = assertNotNull(settlementRepository.findById(id))
        assertEquals(listOf(3L, 5L), found.lines.map { it.sellerOrderId })
        assertEquals(found.lines.map { it.calc }, settlementRepository.findLines(id).map { it.calc })
        assertEquals(SettlementStatus.GENERATED, found.status)
        assertEquals(1L, found.sellerId)
        assertEquals(period, found.period)
        assertTrue(settlementRepository.existsBySellerIdAndPeriodStart(1L, period.start))

        found.confirm(LocalDateTime.of(2026, 11, 10, 9, 0))
        settlementRepository.save(found)
        val confirmed = assertNotNull(settlementRepository.findById(id))
        assertEquals(SettlementStatus.CONFIRMED, confirmed.status)
        assertEquals(LocalDateTime.of(2026, 11, 10, 9, 0), confirmed.confirmedAt)
        assertEquals(2, confirmed.lines.size)
        assertEquals(found.totalAmount, confirmed.totalAmount)
    }

    @Test
    fun findSettledSellerOrderIds는_라인이_있는_것만_준다() {
        settlementRepository.save(generate(1L, period, 1L, 2L))

        assertEquals(setOf(1L, 2L), settlementRepository.findSettledSellerOrderIds(listOf(1L, 2L, 3L)))
        assertEquals(emptySet(), settlementRepository.findSettledSellerOrderIds(emptyList()))
    }

    @Test
    fun attach는_settlement_id_NULL이고_created_at이_기준_전인_ACTIVE_조정만_붙이고_영향_행_수를_준다() {
        val settlement = settlementRepository.save(generate(1L, period, 1L))
        val settlementId = assertNotNull(settlement.id)
        val before = assertNotNull(adjustmentRepository.save(SettlementAdjustment.manual(1L, 500, "전", null, 99L)).id)
        val after = assertNotNull(adjustmentRepository.save(SettlementAdjustment.manual(1L, 700, "후", null, 99L)).id)
        val other = assertNotNull(adjustmentRepository.save(SettlementAdjustment.manual(2L, 900, "다른 판매자", null, 99L)).id)
        val deleted = adjustmentRepository.save(SettlementAdjustment.manual(1L, 100, "삭제", null, 99L))
        adjustmentRepository.delete(deleted)
        val carryOver = assertNotNull(adjustmentRepository.save(SettlementAdjustment.carryOver(1L, -300, 77L)).id)
        val boundary = LocalDateTime.of(2026, 11, 9, 0, 0)
        jdbcTemplate.update("update settlement_adjustment set created_at = ? where id = ?", boundary.minusSeconds(1), before)
        jdbcTemplate.update("update settlement_adjustment set created_at = ? where id = ?", boundary, after)
        jdbcTemplate.update("update settlement_adjustment set created_at = ? where id = ?", boundary.plusDays(30), carryOver)

        assertEquals(setOf(1L), adjustmentRepository.findSellerIdsWithUnattached(boundary))

        val attached = adjustmentRepository.attach(1L, settlementId, boundary)

        assertEquals(2, attached) // before + CARRY_OVER(created_at 무관). after(경계 시각)·삭제·타 판매자는 제외
        assertEquals(setOf(before, carryOver), adjustmentRepository.findBySettlementId(settlementId).map { it.id }.toSet())
        assertNull(adjustmentRepository.findById(after)!!.settlementId)
        assertNull(adjustmentRepository.findById(other)!!.settlementId)
        assertEquals(0, adjustmentRepository.attach(1L, settlementId, boundary)) // 이미 붙은 것은 다시 붙지 않는다
    }

    @Test
    fun 이월_조정은_source_settlement_id가_UNIQUE다() {
        adjustmentRepository.save(SettlementAdjustment.carryOver(1L, -300, 77L))

        assertFailsWith<DataIntegrityViolationException> { adjustmentRepository.save(SettlementAdjustment.carryOver(1L, -400, 77L)) }
        // 수동 조정은 source_settlement_id가 NULL이라 여러 개 가능
        adjustmentRepository.save(SettlementAdjustment.manual(1L, 1, "a", null, 99L))
        adjustmentRepository.save(SettlementAdjustment.manual(1L, 2, "b", null, 99L))
    }

    @Test
    fun 조정_소프트_삭제와_페이지_조회() {
        val a = adjustmentRepository.save(SettlementAdjustment.manual(1L, 1, "a", 5L, 99L))
        val b = adjustmentRepository.save(SettlementAdjustment.manual(1L, 2, "b", null, 99L))
        adjustmentRepository.delete(a)

        val page = adjustmentRepository.findPageBySellerId(1L, 0, 10)

        assertEquals(listOf(b.id), page.content.map { it.id })
        assertNull(adjustmentRepository.findById(requireNotNull(a.id)))
        assertEquals(AdjustmentType.MANUAL, page.content.single().type)
        assertEquals(99L, page.content.single().createdByAdminId)
        assertNotNull(page.content.single().createdAt)
    }

    @Test
    fun settlement_run_period_start는_UNIQUE다() {
        val start = period.start
        runRepository.save(SettlementRun(start, LocalDateTime.of(2026, 11, 9, 3, 0), 2))

        assertTrue(runRepository.exists(start))
        assertEquals(setOf(start), runRepository.findAllPeriodStarts())
        assertFailsWith<DataIntegrityViolationException> { runRepository.save(SettlementRun(start, LocalDateTime.of(2026, 11, 9, 4, 0), 3)) }
    }
}
