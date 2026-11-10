package com.dogyun.kairos.settlement.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.settlement.domain.SettlementAdjustment
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SettlementAdjustmentServiceTest {
    private val repo = FakeSettlementAdjustmentRepository { LocalDateTime.of(2026, 11, 10, 9, 0) }
    private val sellers = FakeSettlementSellerReader(SettlementSellerInfo(7, "쌀롱", "SUSPENDED"))
    private val service = SettlementAdjustmentService(repo, sellers)

    private fun code(block: () -> Unit) = assertFailsWith<DomainException>(block = block).errorCode

    @Test
    fun 금액_0은_SETTLEMENT_ADJUSTMENT_INVALID() {
        assertEquals(ErrorCode.SETTLEMENT_ADJUSTMENT_INVALID, code { service.create(1, 7, CreateAdjustmentCommand(0, "사유", null)) })
    }

    @Test
    fun 없는_판매자는_SELLER_NOT_FOUND이고_정지_판매자는_등록된다() {
        assertEquals(ErrorCode.SELLER_NOT_FOUND, code { service.create(1, 99, CreateAdjustmentCommand(100, "사유", null)) })
        assertNotNull(service.create(1, 7, CreateAdjustmentCommand(100, "정지 판매자도 허용", null)))
    }

    @Test
    fun 붙은_조정_삭제는_SETTLEMENT_ADJUSTMENT_APPLIED() {
        val id = service.create(1, 7, CreateAdjustmentCommand(-500, "벌금", null))
        repo.attach(7, 55, LocalDateTime.of(2026, 11, 11, 0, 0))

        assertEquals(ErrorCode.SETTLEMENT_ADJUSTMENT_APPLIED, code { service.delete(id) })
        assertEquals(55L, repo.findById(id)!!.settlementId)
    }

    @Test
    fun 안_붙은_조정은_삭제되고_두_번째는_SETTLEMENT_NOT_FOUND() {
        val id = service.create(1, 7, CreateAdjustmentCommand(-500, "벌금", null))

        service.delete(id)

        assertEquals(null, repo.findById(id))
        assertEquals(ErrorCode.SETTLEMENT_NOT_FOUND, code { service.delete(id) })
        assertEquals(ErrorCode.SETTLEMENT_NOT_FOUND, code { service.delete(12345) })
    }

    @Test
    fun CARRY_OVER는_안_붙어도_삭제할_수_없다() {
        val id = requireNotNull(repo.save(SettlementAdjustment.carryOver(7, -300, 1)).id)

        assertEquals(ErrorCode.SETTLEMENT_ADJUSTMENT_APPLIED, code { service.delete(id) })
        assertNotNull(repo.findById(id))
    }
}
