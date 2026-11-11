package com.dogyun.kairos.settlement.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** 월~일 주간 정산 기간 `[start 00:00, end 00:00)`(KST, 반열린). 지급일은 다음 주 수요일. */
data class SettlementPeriod private constructor(val start: LocalDate) {
    val end: LocalDate get() = start.plusDays(7)
    val payoutDate: LocalDate get() = start.plusDays(9)
    val startAt: LocalDateTime get() = start.atStartOfDay()
    val endAt: LocalDateTime get() = end.atStartOfDay()

    fun previous(): SettlementPeriod = SettlementPeriod(start.minusDays(7))

    fun next(): SettlementPeriod = SettlementPeriod(start.plusDays(7))

    companion object {
        fun of(start: LocalDate): SettlementPeriod {
            if (start.dayOfWeek != DayOfWeek.MONDAY) throw DomainException(ErrorCode.SETTLEMENT_PERIOD_INVALID)
            return SettlementPeriod(start)
        }

        /** [now] 기준 이미 끝난(end ≤ now) 가장 최근 기간. 월요일 00:00:00 정각이면 방금 끝난 주가 대상이다. */
        fun lastEndedBefore(now: LocalDateTime): SettlementPeriod {
            val thisMonday = now.toLocalDate().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return SettlementPeriod(thisMonday.minusDays(7))
        }
    }
}
