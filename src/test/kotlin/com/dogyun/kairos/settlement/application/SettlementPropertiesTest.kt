package com.dogyun.kairos.settlement.application

import java.time.Duration
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettlementPropertiesTest {

    @Test
    fun 월요일이_아닌_시작일은_기동_실패다() {
        assertFailsWith<IllegalArgumentException> { SettlementProperties(startDate = LocalDate.of(2026, 11, 17)) }
    }

    @Test
    fun 월요일_시작일은_되고_lookback_기본값은_28일이다() {
        assertEquals(Duration.ofDays(28), SettlementProperties(startDate = LocalDate.of(2026, 11, 16)).lateConfirmLookback)
    }
}
