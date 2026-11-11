package com.dogyun.kairos.settlement.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettlementTest {
    private val period = SettlementPeriod.of(LocalDate.of(2026, 11, 16))
    private val now = LocalDateTime.of(2026, 11, 23, 3, 0)

    private fun src(id: Long, items: List<SettlementSourceItem>, shipping: Long = 3000, refunded: Long = 0) =
        SettlementSource(id, 7, 100 + id, "K$id", LocalDateTime.of(2026, 11, 17, 10, 0), 1000, shipping, refunded, items)

    private fun example(adjustment: Long = -10_000): Settlement = Settlement.generate(
        7, period,
        listOf(
            src(3, listOf(SettlementSourceItem(5, false, 10_000, 0, null, 3_000)), refunded = 3_000),
            src(1, listOf(SettlementSourceItem(1, true, 50_000, 5_000, "SELLER", 0), SettlementSourceItem(2, true, 30_000, 0, null, 0))),
            src(2, listOf(SettlementSourceItem(3, false, 20_000, 0, null, 3_000), SettlementSourceItem(4, true, 15_000, 0, null, 0))),
        ),
        adjustment,
    )

    private fun code(block: () -> Unit) = assertFailsWith<DomainException> { block() }.errorCode

    @Test
    fun generate는_라인을_sellerOrderId_오름차순으로_두고_헤더_합이_라인_합이다() {
        val s = example()

        assertEquals(listOf(1L, 2L, 3L), s.lines.map { it.sellerOrderId })
        assertEquals(3, s.lineCount)
        assertEquals(s.lines.sumOf { it.calc.salesAmount }, s.salesAmount)
        assertEquals(95_000, s.salesAmount)
        assertEquals(5_000, s.sellerCouponDiscount)
        assertEquals(9_000, s.commissionAmount)
        assertEquals(6_000, s.shippingFeeOwed)
        assertEquals(6_000, s.returnFeeBorne)
        assertEquals(-10_000, s.adjustmentAmount)
        assertEquals(s.lines.sumOf { it.calc.lineAmount } + s.adjustmentAmount, s.totalAmount)
        assertEquals(SettlementStatus.GENERATED, s.status)
    }

    @Test
    fun total_71000_payable_71000_carryOver_0() {
        val s = example()

        assertEquals(71_000, s.totalAmount)
        assertEquals(71_000, s.payableAmount)
        assertEquals(0, s.carryOverAmount)
    }

    @Test
    fun total_음수면_payable_0_carryOver는_total이다() {
        val s = Settlement.generate(7, period, listOf(src(1, listOf(SettlementSourceItem(1, false, 1_000, 0, null, 3_000)), refunded = 3_000)), 0)

        assertEquals(-3_000, s.totalAmount)
        assertEquals(0, s.payableAmount)
        assertEquals(-3_000, s.carryOverAmount)
    }

    @Test
    fun applyAdjustments는_GENERATED에서만_합계를_다시_계산한다() {
        val s = example(0)
        s.applyAdjustments(-10_000)
        assertEquals(71_000, s.totalAmount)
        assertEquals(-10_000, s.adjustmentAmount)

        s.confirm(now)
        assertFailsWith<IllegalStateException> { s.applyAdjustments(0) }
    }

    @Test
    fun GENERATED에서_markPaid는_SETTLEMENT_INVALID_STATE_TRANSITION() {
        assertEquals(ErrorCode.SETTLEMENT_INVALID_STATE_TRANSITION, code { example().markPaid(now, null) })
    }

    @Test
    fun CONFIRMED_markPaid_두_번은_멱등이다() {
        val s = example()
        s.confirm(now)
        s.confirm(now.plusDays(1))
        assertEquals(now, s.confirmedAt)

        s.markPaid(now.plusDays(2), "1차 이체")
        s.markPaid(now.plusDays(3), "다른 메모")

        assertEquals(SettlementStatus.PAID, s.status)
        assertEquals(now.plusDays(2), s.paidAt)
        assertEquals("1차 이체", s.paidMemo)
        s.confirm(now.plusDays(4))
        assertEquals(SettlementStatus.PAID, s.status)
    }

    @Test
    fun 지급_메모는_200자까지다() {
        val s = example()
        s.confirm(now)

        assertEquals(ErrorCode.INVALID_REQUEST, code { s.markPaid(now, "a".repeat(201)) })
        s.markPaid(now, "a".repeat(200))
        assertEquals(SettlementStatus.PAID, s.status)
    }

    @Test
    fun 조정_금액_0은_SETTLEMENT_ADJUSTMENT_INVALID() {
        assertEquals(ErrorCode.SETTLEMENT_ADJUSTMENT_INVALID, code { SettlementAdjustment.manual(7, 0, "사유", null, 1) })
        assertEquals(ErrorCode.SETTLEMENT_ADJUSTMENT_INVALID, code { SettlementAdjustment.manual(7, 100, "", null, 1) })
        assertEquals(ErrorCode.SETTLEMENT_ADJUSTMENT_INVALID, code { SettlementAdjustment.manual(7, 100, "a".repeat(501), null, 1) })
        val ok = SettlementAdjustment.manual(7, -10_000, "a".repeat(500), 9, 1)
        assertEquals(AdjustmentType.MANUAL, ok.type)
        assertFalse(ok.applied)
        assertNull(ok.sourceSettlementId)
    }

    @Test
    fun carryOver는_음수만_받고_사유에_원천_정산_번호가_들어간다() {
        val c = SettlementAdjustment.carryOver(7, -3_000, 42)

        assertEquals(AdjustmentType.CARRY_OVER, c.type)
        assertEquals("정산 #42 음수 이월", c.reason)
        assertEquals(42L, c.sourceSettlementId)
        assertFailsWith<IllegalArgumentException> { SettlementAdjustment.carryOver(7, 0, 42) }
        assertTrue(SettlementAdjustment(1, 7, 5, AdjustmentType.MANUAL, 1, "r", null, null, 1, null).applied)
    }

    @Test
    fun 다른_판매자의_주문이_섞이면_생성을_거절한다() {
        val other = SettlementSource(9, 8, 109, "K9", LocalDateTime.of(2026, 11, 17, 10, 0), 1000, 0, 0, emptyList())
        assertFailsWith<IllegalStateException> { Settlement.generate(7, period, listOf(other), 0) }
        val g = Settlement.generate(8, period, listOf(other), 0)
        assertEquals(8, g.sellerId)
        assertNull(g.id)
    }
}
