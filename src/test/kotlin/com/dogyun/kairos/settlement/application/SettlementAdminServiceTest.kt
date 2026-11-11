package com.dogyun.kairos.settlement.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.settlement.domain.Settlement
import com.dogyun.kairos.settlement.domain.SettlementPeriod
import com.dogyun.kairos.settlement.domain.SettlementStatus
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettlementAdminServiceTest {
    private val kst = ZoneId.of("Asia/Seoul")
    private val settlements = FakeSettlementRepository()
    private var now = LocalDateTime.of(2026, 11, 24, 10, 0)
    private val clock = object : Clock() {
        override fun getZone() = kst
        override fun withZone(zone: ZoneId?) = this
        override fun instant() = now.atZone(kst).toInstant()
    }
    private val service = SettlementAdminService(
        settlements,
        SettlementQueryService(settlements, FakeSettlementAdjustmentRepository { now }, FakeSettlementSellerReader(SettlementSellerInfo(7, "쌀롱", "APPROVED"))),
        clock,
    )

    private fun generated(): Long =
        requireNotNull(settlements.save(Settlement.generate(7, SettlementPeriod.of(LocalDate.of(2026, 11, 16)), emptyList(), 0)).id)

    @Test
    fun GENERATED에서_pay는_409이고_confirm_뒤_pay_두_번은_멱등이다() {
        val id = generated()
        assertEquals(
            ErrorCode.SETTLEMENT_INVALID_STATE_TRANSITION,
            assertFailsWith<DomainException> { service.markPaid(id, "직행") }.errorCode,
        )

        val confirmed = service.confirm(id)
        now = now.plusHours(1)
        val again = service.confirm(id)
        assertEquals(SettlementStatus.CONFIRMED, again.status)
        assertEquals(confirmed.confirmedAt, again.confirmedAt)

        val paid = service.markPaid(id, "11/25 이체")
        val paidAgain = service.markPaid(id, "다른 메모")
        assertEquals(SettlementStatus.PAID, paidAgain.status)
        assertEquals(paid.paidAt, paidAgain.paidAt)
        assertEquals("11/25 이체", paidAgain.paidMemo)
        assertEquals("쌀롱", paidAgain.sellerName)
    }

    @Test
    fun 없는_정산은_SETTLEMENT_NOT_FOUND이고_메모_200자_초과는_INVALID_REQUEST() {
        assertEquals(ErrorCode.SETTLEMENT_NOT_FOUND, assertFailsWith<DomainException> { service.confirm(999) }.errorCode)
        val id = generated()
        service.confirm(id)
        assertEquals(ErrorCode.INVALID_REQUEST, assertFailsWith<DomainException> { service.markPaid(id, "가".repeat(201)) }.errorCode)
    }
}
