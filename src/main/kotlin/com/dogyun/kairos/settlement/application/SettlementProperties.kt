package com.dogyun.kairos.settlement.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate

/**
 * 정산 설정. [startDate] 이전에 확정된 주문은 정산하지 않고, [lateConfirmLookback] 은 기간 시작 전에 확정됐지만
 * 아직 어느 정산에도 없는 판매자 주문을 얼마나 거슬러 찾을지다.
 */
@ConfigurationProperties("kairos.settlement")
data class SettlementProperties(
    val lateConfirmLookback: Duration = Duration.ofDays(28),
    val startDate: LocalDate,
) {
    init {
        require(startDate.dayOfWeek == DayOfWeek.MONDAY) { "kairos.settlement.start-date 는 월요일이어야 한다: $startDate" }
        require(!lateConfirmLookback.isNegative) { "kairos.settlement.late-confirm-lookback 은 0 이상이어야 한다: $lateConfirmLookback" }
    }
}
