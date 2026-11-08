package com.dogyun.kairos.deal.application

import com.dogyun.kairos.IntegrationTestSupport
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.deal.FixedClockConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 다른 모듈(order)이 호출할 서비스 계약 검증. 고정 시각: 2026-12-08T12:00 */
@Import(FixedClockConfig::class)
class DealApplicationServiceTest : IntegrationTestSupport() {

    @Autowired
    private lateinit var dealService: DealService

    @Autowired
    private lateinit var dealQueryService: DealQueryService

    @Autowired
    private lateinit var stockService: StockService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeTest
    fun cleanUp() {
        jdbcTemplate.update("delete from stock")
        jdbcTemplate.update("delete from deal")
    }

    private fun create(openAt: String, closeAt: String, total: Int = 5) = dealService.create(
        CreateDealCommand("딜", 10000, 5000, total, 2, LocalDateTime.parse(openAt), LocalDateTime.parse(closeAt)),
    )

    @Test
    fun 스냅샷은_진행여부를_시각으로_계산한다() {
        val ongoing = create("2026-12-08T10:00:00", "2026-12-09T10:00:00")
        val scheduled = create("2026-12-09T10:00:00", "2026-12-10T10:00:00")

        assertEquals(DealSnapshot(ongoing, 5000, 2, true), dealQueryService.getSnapshot(ongoing))
        assertFalse(dealQueryService.getSnapshot(scheduled).ongoing)
    }

    @Test
    fun 없는_딜_스냅샷은_DEAL_NOT_FOUND() {
        val e = assertFailsWith<DomainException> { dealQueryService.getSnapshot(999999) }
        assertEquals(ErrorCode.DEAL_NOT_FOUND, e.errorCode)
    }

    @Test
    fun 재고_차감과_복구() {
        val dealId = create("2026-12-08T10:00:00", "2026-12-09T10:00:00", total = 5)

        stockService.decrease(dealId, 5)
        assertEquals(0, dealQueryService.detail(dealId).remainingQuantity)
        val soldOut = assertFailsWith<DomainException> { stockService.decrease(dealId, 1) }
        assertEquals(ErrorCode.STOCK_SOLD_OUT, soldOut.errorCode)

        stockService.restore(dealId, 5)
        assertEquals(5, dealQueryService.detail(dealId).remainingQuantity)
    }

    @Test
    fun 없는_딜의_재고_차감은_DEAL_NOT_FOUND() {
        val e = assertFailsWith<DomainException> { stockService.decrease(999999, 1) }
        assertEquals(ErrorCode.DEAL_NOT_FOUND, e.errorCode)
    }

    @Test
    fun 오픈_후_삭제는_거절되고_딜이_남는다() {
        val dealId = create("2026-12-08T10:00:00", "2026-12-09T10:00:00")
        assertFailsWith<DomainException> { dealService.delete(dealId) }
        assertTrue(dealQueryService.detail(dealId).dealId == dealId)
    }
}
