package com.dogyun.kairos.payment.infrastructure

import com.dogyun.kairos.payment.application.PgResult
import com.dogyun.kairos.payment.application.PgStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MockPgClientTest {

    private val pg = MockPgClient()

    private fun done(r: PgResult) = assertIs<PgResult.Done>(r).payment

    @Test
    fun 일반_키는_Done이고_find로_다시_조회된다() {
        val p = done(pg.confirm("pay-1", "ORD-1", 10_000L))
        assertEquals(PgStatus.DONE, p.status)
        assertEquals(10_000L, p.totalAmount)
        assertEquals(10_000L, p.balanceAmount)
        assertEquals("ORD-1", p.pgOrderId)
        assertEquals(p, done(pg.find("pay-1")))
    }

    @Test
    fun fail_접두사는_Failed() {
        assertIs<PgResult.Failed>(pg.confirm("fail-1", "ORD-1", 1_000L))
    }

    @Test
    fun unknown_접두사는_Unknown이지만_find는_Done이다() {
        assertIs<PgResult.Unknown>(pg.confirm("unknown-1", "ORD-1", 1_000L))
        assertEquals(PgStatus.DONE, done(pg.find("unknown-1")).status)
    }

    @Test
    fun lost_접두사는_Unknown이고_find는_NOT_FOUND다() {
        assertIs<PgResult.Unknown>(pg.confirm("lost-1", "ORD-1", 1_000L))
        assertEquals("NOT_FOUND", assertIs<PgResult.Failed>(pg.find("lost-1")).code)
    }

    @Test
    fun cancelunknown_키의_취소는_Unknown이지만_find의_cancels에_있다() {
        pg.confirm("cancelunknown-1", "ORD-1", 5_000L)
        assertIs<PgResult.Unknown>(pg.cancel("cancelunknown-1", 2_000L, "사유", "idem-1"))
        val p = done(pg.find("cancelunknown-1"))
        assertEquals(1, p.cancels.size)
        assertEquals(2_000L, p.cancels[0].amount)
        assertEquals(3_000L, p.balanceAmount)
    }

    @Test
    fun cancellost_키의_취소는_Unknown이고_cancels에_없다() {
        pg.confirm("cancellost-1", "ORD-1", 5_000L)
        assertIs<PgResult.Unknown>(pg.cancel("cancellost-1", 2_000L, "사유", "idem-1"))
        val p = done(pg.find("cancellost-1"))
        assertTrue(p.cancels.isEmpty())
        assertEquals(5_000L, p.balanceAmount)
    }

    @Test
    fun cancelfail_키의_취소는_Failed다() {
        pg.confirm("cancelfail-1", "ORD-1", 5_000L)
        assertIs<PgResult.Failed>(pg.cancel("cancelfail-1", 2_000L, "사유", "idem-1"))
        assertTrue(done(pg.find("cancelfail-1")).cancels.isEmpty())
    }

    @Test
    fun 같은_멱등_키_취소는_한_번만_기록된다() {
        pg.confirm("pay-1", "ORD-1", 5_000L)
        val first = done(pg.cancel("pay-1", 2_000L, "사유", "idem-1"))
        val second = done(pg.cancel("pay-1", 2_000L, "사유", "idem-1"))
        assertEquals(first, second)
        assertEquals(1, done(pg.find("pay-1")).cancels.size)
        assertEquals(3_000L, done(pg.find("pay-1")).balanceAmount)
    }

    @Test
    fun 취소_후_balanceAmount와_cancels가_맞다() {
        pg.confirm("pay-1", "ORD-1", 5_000L)
        val partial = done(pg.cancel("pay-1", 2_000L, "단순 변심", "idem-1"))
        assertEquals(PgStatus.PARTIAL_CANCELED, partial.status)
        assertEquals(3_000L, partial.balanceAmount)
        val full = done(pg.cancel("pay-1", 3_000L, "나머지", "idem-2"))
        assertEquals(PgStatus.CANCELED, full.status)
        assertEquals(0L, full.balanceAmount)
        assertEquals(listOf(2_000L, 3_000L), full.cancels.map { it.amount })
        assertEquals("단순 변심", full.cancels[0].reason)
    }
}
