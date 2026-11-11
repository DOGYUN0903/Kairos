package com.dogyun.kairos.settlement.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettlementPeriodTest {

    @Test
    fun 화요일은_SETTLEMENT_PERIOD_INVALID() {
        val e = assertFailsWith<DomainException> { SettlementPeriod.of(LocalDate.of(2026, 11, 17)) }
        assertEquals(ErrorCode.SETTLEMENT_PERIOD_INVALID, e.errorCode)
    }

    @Test
    fun `2026_11_16_기간의_end는_11_23_payout은_11_25다`() {
        val p = SettlementPeriod.of(LocalDate.of(2026, 11, 16))

        assertEquals(LocalDate.of(2026, 11, 23), p.end)
        assertEquals(LocalDate.of(2026, 11, 25), p.payoutDate)
        assertEquals(LocalDateTime.of(2026, 11, 16, 0, 0), p.startAt)
        assertEquals(LocalDateTime.of(2026, 11, 23, 0, 0), p.endAt)
        assertEquals(LocalDate.of(2026, 11, 9), p.previous().start)
    }

    @Test
    fun lastEndedBefore는_반열린_구간이라_월요일_00시_00분_00초는_새_기간이다() {
        // 월요일 00:00:00 정각: 방금 끝난 주(11-09 ~ 11-16)가 대상
        assertEquals(LocalDate.of(2026, 11, 9), SettlementPeriod.lastEndedBefore(LocalDateTime.of(2026, 11, 16, 0, 0, 0)).start)
        // 1초 전: 아직 11-16 주가 안 끝났으므로 그 전 주(11-02)
        assertEquals(LocalDate.of(2026, 11, 2), SettlementPeriod.lastEndedBefore(LocalDateTime.of(2026, 11, 15, 23, 59, 59)).start)
        // 수요일
        assertEquals(LocalDate.of(2026, 11, 9), SettlementPeriod.lastEndedBefore(LocalDateTime.of(2026, 11, 18, 12, 0)).start)
    }
}
