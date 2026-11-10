package com.dogyun.kairos.settlement.application

import com.dogyun.kairos.common.application.InlineTransactionRunner
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.settlement.domain.AdjustmentType
import com.dogyun.kairos.settlement.domain.SettlementAdjustment
import com.dogyun.kairos.settlement.domain.SettlementRun
import com.dogyun.kairos.settlement.domain.SettlementSource
import com.dogyun.kairos.settlement.domain.SettlementSourceItem
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettlementGenerationServiceTest {
    private val kst = ZoneId.of("Asia/Seoul")
    private val p0 = LocalDate.of(2026, 11, 9)
    private val p1 = LocalDate.of(2026, 11, 16)
    private val p2 = LocalDate.of(2026, 11, 23)

    private inner class Env(
        now: LocalDateTime = LocalDateTime.of(2026, 11, 24, 3, 0),
        startDate: LocalDate = p0,
        lookback: Duration = Duration.ofDays(28),
        doneRuns: List<LocalDate> = listOf(p0),
    ) {
        val clock: Clock = Clock.fixed(now.atZone(kst).toInstant(), kst)
        val reader = FakeConfirmedSellerOrderReader()
        val settlements = FakeSettlementRepository()
        val adjustments = FakeSettlementAdjustmentRepository { LocalDateTime.now(clock) }
        val runs = FakeSettlementRunRepository().also { r -> doneRuns.forEach { r.save(SettlementRun(it, now, 0)) } }
        val service = SettlementGenerationService(
            reader, settlements, adjustments, runs, SettlementProperties(lookback, startDate), InlineTransactionRunner(), clock,
        )

        fun adjust(sellerId: Long, amount: Long, at: LocalDateTime) =
            adjustments.save(SettlementAdjustment(null, sellerId, null, AdjustmentType.MANUAL, amount, "조정", null, null, 1, at))

        /** id 를 빼고 판매자·기간으로 비교할 수 있는 저장소 내용. */
        fun snapshot(): List<Any> {
            val keyOf = settlements.rows.mapValues { (_, s) -> s.sellerId to s.period.start }
            val ss = settlements.rows.values.sortedWith(compareBy({ it.period.start }, { it.sellerId })).map { s ->
                listOf(
                    s.sellerId, s.period.start, s.status, s.salesAmount, s.sellerCouponDiscount, s.commissionAmount, s.shippingFeeOwed,
                    s.returnFeeBorne, s.adjustmentAmount, s.totalAmount, s.payableAmount, s.lineCount,
                    s.lines.map { listOf(it.sellerOrderId, it.orderId, it.orderNo, it.confirmedAt, it.calc) },
                )
            }
            val aa = adjustments.rows.values.map {
                listOf(it.sellerId, it.settlementId?.let(keyOf::get), it.type, it.amount, it.reason.substringBefore('#'), it.sourceSettlementId?.let(keyOf::get))
            }.sortedBy { it.toString() }
            // settlement_run.seller_count 는 "그 실행이 다룬 판매자 수"라 중단·재실행이면 달라질 수 있어 비교에서 뺀다.
            return listOf(ss, aa, runs.runs.map { it.periodStart })
        }
    }

    private fun src(id: Long, sellerId: Long, confirmedAt: LocalDateTime, items: List<SettlementSourceItem>, refunded: Long = 0) =
        SettlementSource(id, sellerId, 100 + id, "K$id", confirmedAt, 1000, 3000, refunded, items)

    private fun simple(id: Long, sellerId: Long, confirmedAt: LocalDateTime, amount: Long = 10_000) =
        src(id, sellerId, confirmedAt, listOf(SettlementSourceItem(id * 10, true, amount, 0, null, 0)))

    private val inP1 = LocalDateTime.of(2026, 11, 17, 10, 0)

    private fun spec87(env: Env, sellerId: Long = 7) {
        env.reader.sources += listOf(
            src(3, sellerId, inP1, listOf(SettlementSourceItem(5, false, 10_000, 0, null, 3_000)), refunded = 3_000),
            src(1, sellerId, inP1, listOf(SettlementSourceItem(1, true, 50_000, 5_000, "SELLER", 0), SettlementSourceItem(2, true, 30_000, 0, null, 0))),
            src(2, sellerId, inP1, listOf(SettlementSourceItem(3, false, 20_000, 0, null, 3_000), SettlementSourceItem(4, true, 15_000, 0, null, 0))),
        )
        env.adjust(sellerId, -10_000, LocalDateTime.of(2026, 11, 18, 9, 0))
    }

    private fun code(block: () -> Unit) = assertFailsWith<DomainException> { block() }.errorCode

    @Test
    fun 스펙_8_7_세_라인과_조정으로_total_71000_payable_71000_라인_순서_sellerOrderId_오름차순() {
        val env = Env()
        spec87(env)

        val r = env.service.generate(p1)

        assertEquals(GenerateSettlementResult(p1, p2, 1, 1, 0, 0, false), r)
        val s = env.settlements.bySeller(7, p1)!!
        assertEquals(listOf(1L, 2L, 3L), s.lines.map { it.sellerOrderId })
        assertEquals(-10_000, s.adjustmentAmount)
        assertEquals(71_000, s.totalAmount)
        assertEquals(71_000, s.payableAmount)
        assertEquals(s.id, env.adjustments.rows.values.single().settlementId)
        assertTrue(env.runs.exists(p1))
    }

    @Test
    fun 화요일_또는_아직_안_끝난_기간은_SETTLEMENT_PERIOD_INVALID() {
        val env = Env()
        assertEquals(ErrorCode.SETTLEMENT_PERIOD_INVALID, code { env.service.generate(LocalDate.of(2026, 11, 17)) })
        env.runs.save(SettlementRun(p1, LocalDateTime.now(env.clock), 0))
        assertEquals(ErrorCode.SETTLEMENT_PERIOD_INVALID, code { env.service.generate(p2) })
        assertEquals(ErrorCode.SETTLEMENT_PERIOD_INVALID, code { env.service.generate(LocalDate.of(2026, 11, 2)) })
    }

    @Test
    fun 직전_기간이_미생성이면_SETTLEMENT_PERIOD_INVALID() {
        val env = Env(doneRuns = emptyList())
        spec87(env)

        assertEquals(ErrorCode.SETTLEMENT_PERIOD_INVALID, code { env.service.generate(p1) })
        assertTrue(env.settlements.rows.isEmpty())
    }

    @Test
    fun 같은_기간_재실행은_새_행_0개_alreadyGenerated_true() {
        val env = Env()
        spec87(env)
        env.service.generate(p1)
        val before = env.snapshot()

        val r = env.service.generate(p1)

        assertTrue(r.alreadyGenerated)
        assertEquals(0, r.createdCount)
        assertEquals(before, env.snapshot())
    }

    @Test
    fun 이미_라인이_있는_판매자_주문은_빠진다() {
        val env = Env()
        val early = simple(1, 7, LocalDateTime.of(2026, 11, 10, 10, 0))
        env.settlements.save(com.dogyun.kairos.settlement.domain.Settlement.generate(7, com.dogyun.kairos.settlement.domain.SettlementPeriod.of(p0), listOf(early), 0))
        env.reader.sources += listOf(early, simple(2, 7, inP1))

        env.service.generate(p1)

        assertEquals(listOf(2L), env.settlements.bySeller(7, p1)!!.lines.map { it.sellerOrderId })
    }

    @Test
    fun lookback_안_미정산_확정분은_들어가고_lookback_밖은_안_들어간다() {
        val env = Env(startDate = LocalDate.of(2026, 10, 5), lookback = Duration.ofDays(7), doneRuns = listOf(p0))
        env.reader.sources += listOf(
            simple(1, 7, LocalDateTime.of(2026, 11, 8, 23, 59)), // lookback(11-09 00:00) 밖
            simple(2, 7, LocalDateTime.of(2026, 11, 9, 0, 0)), // lookback 시작 경계(포함)
            simple(3, 7, inP1),
            simple(4, 7, LocalDateTime.of(2026, 11, 23, 0, 0)), // 기간 끝(미포함)
        )

        env.service.generate(p1)

        assertEquals(listOf(2L, 3L), env.settlements.bySeller(7, p1)!!.lines.map { it.sellerOrderId })
    }

    @Test
    fun start_date_전_확정분은_안_들어간다() {
        val env = Env()
        env.reader.sources += listOf(simple(1, 7, LocalDateTime.of(2026, 11, 8, 23, 0)), simple(2, 7, LocalDateTime.of(2026, 11, 9, 1, 0)), simple(3, 7, inP1))

        env.service.generate(p1)

        assertEquals(listOf(2L, 3L), env.settlements.bySeller(7, p1)!!.lines.map { it.sellerOrderId })
    }

    @Test
    fun 확정_0건_조정만_있는_판매자는_라인_0개_정산이_생긴다() {
        val env = Env()
        env.adjust(8, 5_000, inP1)

        val r = env.service.generate(p1)

        val s = env.settlements.bySeller(8, p1)!!
        assertEquals(1, r.createdCount)
        assertEquals(0, s.lineCount)
        assertEquals(5_000, s.totalAmount)
        assertEquals(5_000, s.payableAmount)
    }

    @Test
    fun total_음수면_payable_0이고_CARRY_OVER_조정이_settlement_id_NULL로_생겨_다음_기간에_붙는다() {
        val env = Env(now = LocalDateTime.of(2026, 12, 1, 3, 0))
        env.reader.sources += simple(1, 7, inP1, amount = 10_000) // line = 10000 - 1000 + 3000 = 12000
        env.adjust(7, -30_000, inP1)

        env.service.generate(p1)
        val first = env.settlements.bySeller(7, p1)!!
        assertEquals(-18_000, first.totalAmount)
        assertEquals(0, first.payableAmount)
        val carry = env.adjustments.rows.values.single { it.type == AdjustmentType.CARRY_OVER }
        assertEquals(-18_000, carry.amount)
        assertNull(carry.settlementId)
        assertEquals(first.id, carry.sourceSettlementId)

        env.reader.sources += simple(2, 7, LocalDateTime.of(2026, 11, 24, 10, 0), amount = 30_000) // 30000 - 3000 + 3000
        env.service.generate(p2)
        val second = env.settlements.bySeller(7, p2)!!
        assertEquals(second.id, env.adjustments.findById(carry.id!!)!!.settlementId)
        assertEquals(-18_000, second.adjustmentAmount)
        assertEquals(12_000, second.totalAmount)
        assertEquals(12_000, second.payableAmount)
        assertEquals(1, env.adjustments.rows.values.count { it.type == AdjustmentType.CARRY_OVER })
    }

    @Test
    fun 기간_끝_뒤_등록한_조정은_붙지_않는다() {
        val env = Env()
        env.reader.sources += simple(1, 7, inP1)
        val late = env.adjust(7, -1_000, LocalDateTime.of(2026, 11, 23, 0, 0))
        env.adjust(9, -1_000, LocalDateTime.of(2026, 11, 23, 10, 0))

        val r = env.service.generate(p1)

        assertEquals(1, r.sellerCount)
        assertEquals(0, env.settlements.bySeller(7, p1)!!.adjustmentAmount)
        assertNull(env.adjustments.findById(late.id!!)!!.settlementId)
        assertNull(env.settlements.bySeller(9, p1))
    }

    private fun threeSellers(env: Env) {
        spec87(env, 7)
        env.reader.sources += listOf(simple(11, 8, inP1), simple(12, 9, inP1))
        env.adjust(8, -50_000, inP1)
    }

    @Test
    fun `2번째_판매자에서_예외가_나면_run이_없고_재실행이_나머지를_만들어_한_번에_끝난_결과와_같다`() {
        val once = Env().also { threeSellers(it) }
        once.service.generate(p1)

        val env = Env().also { threeSellers(it) }
        env.settlements.failOnSave(8)
        val r1 = env.service.generate(p1)
        assertEquals(GenerateSettlementResult(p1, p2, 3, 2, 0, 1, false), r1)
        assertFalse(env.runs.exists(p1))
        env.settlements.clearFailures()
        val r2 = env.service.generate(p1)

        // 이미 끝난 판매자는 주문·조정이 모두 붙어 대상에서 빠진다.
        assertEquals(GenerateSettlementResult(p1, p2, 1, 1, 0, 0, false), r2)
        assertTrue(env.runs.exists(p1))
        assertEquals(once.snapshot(), env.snapshot())
    }

    @Test
    fun generateDue는_오래된_기간부터_만들고_빈_기간도_run을_남긴다() {
        val env = Env(startDate = LocalDate.of(2026, 10, 26), doneRuns = emptyList())
        spec87(env)

        val count = env.service.generateDue()

        assertEquals(4, count)
        assertEquals(
            listOf(LocalDate.of(2026, 10, 26) to 0, LocalDate.of(2026, 11, 2) to 0, p0 to 0, p1 to 1),
            env.runs.runs.map { it.periodStart to it.sellerCount },
        )
        assertEquals(0, env.service.generateDue())
    }

    @Test
    fun generateDue는_실패한_기간에서_멈추고_다음_기간을_만들지_않는다() {
        val env = Env(now = LocalDateTime.of(2026, 12, 1, 3, 0))
        threeSellers(env)
        env.reader.sources += simple(21, 9, LocalDateTime.of(2026, 11, 24, 10, 0))
        env.settlements.failOnSave(8)

        assertEquals(0, env.service.generateDue())
        assertFalse(env.runs.exists(p1))
        assertNull(env.settlements.bySeller(9, p2))

        env.settlements.clearFailures()
        assertEquals(2, env.service.generateDue())
        assertTrue(env.runs.exists(p2))
    }

    @Test
    fun 정지_판매자도_정산이_생긴다() {
        // reader 는 판매자 상태를 보지 않는다: 정지 여부와 무관하게 확정분이면 정산한다.
        val env = Env()
        env.reader.sources += simple(1, 99, inP1)

        env.service.generate(p1)

        assertEquals(1, env.settlements.bySeller(99, p1)!!.lineCount)
    }
}
