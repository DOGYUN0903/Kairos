package com.dogyun.kairos.benefit.application

import com.dogyun.kairos.benefit.domain.PointRefType
import com.dogyun.kairos.benefit.domain.PointSourceType
import com.dogyun.kairos.benefit.domain.PointTransactionType
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PointServiceTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val start = LocalDateTime.of(2026, 11, 10, 12, 0, 0)
    private val repo = FakePointRepository()
    private var now: Instant = start.atZone(seoul).toInstant()
    private val clock: Clock = object : Clock() {
        override fun getZone(): ZoneId = seoul
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }
    private val service = PointService(repo, BenefitProperties(), clock)
    private val memberId = 7L

    private fun advanceDays(days: Long) {
        now = now.plusSeconds(days * 86_400)
    }

    private fun grant(amount: Long, expiresInDays: Long, refId: Long): Long {
        service.earn(memberId, amount, PointTransactionType.EARN_PURCHASE, PointSourceType.PURCHASE, PointRefType.PURCHASE_CONFIRM, refId, start.plusDays(expiresInDays))
        return repo.lots.values.single { it.sourceId == refId }.id!!
    }

    /** 300(+10d), 500(+5d), 400(+20d) 를 쌓고 로트 id 를 (300, 500, 400) 순으로 돌려준다. */
    private fun threeLots(): Triple<Long, Long, Long> = Triple(grant(300, 10, 1), grant(500, 5, 2), grant(400, 20, 3))

    @Test
    fun 적립하면_로트와_원장과_잔액이_생긴다() {
        val earned = service.earn(memberId, 1_000, PointTransactionType.EARN_REVIEW, PointSourceType.REVIEW, PointRefType.REVIEW, 5, start.plusDays(365))

        assertTrue(earned)
        assertEquals(1_000, repo.balance(memberId))
        val lot = repo.lots.values.single()
        assertEquals(1_000, lot.amount)
        assertEquals(1_000, lot.remainingAmount)
        assertEquals(PointSourceType.REVIEW, lot.sourceType)
        val tx = repo.transactions.values.single()
        assertEquals(PointTransactionType.EARN_REVIEW, tx.type)
        assertEquals(1_000, tx.amount)
        assertEquals(1_000, tx.balanceAfter)
    }

    @Test
    fun 같은_ref로_두_번_적립해도_한_번이다() {
        val first = service.earnForReview(memberId, 9, hasPhoto = true)
        val second = service.earnForReview(memberId, 9, hasPhoto = true)

        assertEquals(500, first)
        assertEquals(0, second)
        assertEquals(500, repo.balance(memberId))
        assertEquals(1, repo.lots.size)
        assertEquals(1, repo.transactions.size)
    }

    @Test
    fun 구매_적립은_floor_1퍼센트이고_0이면_기록하지_않는다() {
        assertEquals(214, service.earnForPurchase(memberId, 1, 21_438))
        assertEquals(214, repo.balance(memberId))
        assertEquals(start.plusDays(365), repo.lot(1).expiresAt)

        assertEquals(0, service.earnForPurchase(memberId, 2, 50))
        assertEquals(1, repo.transactions.size)
        assertEquals(1, repo.lots.size)
    }

    @Test
    fun 사용은_만료가_빠른_로트부터_뺀다() {
        val (lot300, lot500, lot400) = threeLots()

        service.use(memberId, 100, 600)

        assertEquals(0, repo.lot(lot500).remainingAmount)
        assertEquals(200, repo.lot(lot300).remainingAmount)
        assertEquals(400, repo.lot(lot400).remainingAmount)
        assertEquals(600, repo.balance(memberId))
        val use = repo.findTransaction(PointTransactionType.USE, PointRefType.ORDER, 100)!!
        assertEquals(-600, use.amount)
        assertEquals(2, use.usages.size)
    }

    @Test
    fun 잔액보다_많이_쓰면_POINT_INSUFFICIENT() {
        grant(300, 10, 1)

        val e = assertFailsWith<DomainException> { service.use(memberId, 100, 301) }

        assertEquals(ErrorCode.POINT_INSUFFICIENT, e.errorCode)
        assertEquals(300, repo.balance(memberId))
    }

    @Test
    fun 사용액이_0이면_아무것도_하지_않고_같은_주문에_두_번_쓰면_예외다() {
        grant(300, 10, 1)
        service.use(memberId, 100, 0)
        assertEquals(0, repo.transactions.values.count { it.type == PointTransactionType.USE })

        service.use(memberId, 100, 100)
        assertFailsWith<IllegalStateException> { service.use(memberId, 100, 100) }
        assertEquals(200, repo.balance(memberId))
    }

    @Test
    fun 되돌림은_usages_역순으로_로트에_돌아온다() {
        val (lot300, lot500, lot400) = threeLots()
        service.use(memberId, 100, 600)

        assertTrue(service.refund(memberId, 100, PointRefType.ORDER_ITEM, 11, 300))

        assertEquals(300, repo.lot(lot300).remainingAmount)
        assertEquals(200, repo.lot(lot500).remainingAmount)
        assertEquals(400, repo.lot(lot400).remainingAmount)
        assertEquals(900, repo.balance(memberId))
        assertEquals(repo.remainingSum(memberId), repo.balance(memberId))
    }

    @Test
    fun 항목별로_나눠_되돌려도_합이_사용액이면_로트가_원래대로다() {
        val (lot300, lot500, lot400) = threeLots()
        service.use(memberId, 100, 600)

        assertTrue(service.refund(memberId, 100, PointRefType.ORDER_ITEM, 11, 250))
        assertTrue(service.refund(memberId, 100, PointRefType.ORDER_ITEM, 12, 350))

        assertEquals(300, repo.lot(lot300).remainingAmount)
        assertEquals(500, repo.lot(lot500).remainingAmount)
        assertEquals(400, repo.lot(lot400).remainingAmount)
        assertEquals(1_200, repo.balance(memberId))
    }

    @Test
    fun 되돌릴_양이_사용_잔량보다_크면_예외다() {
        grant(300, 10, 1)
        service.use(memberId, 100, 200)

        assertFailsWith<IllegalStateException> { service.refund(memberId, 100, PointRefType.ORDER, 100, 201) }
        assertEquals(100, repo.balance(memberId))
    }

    @Test
    fun 만료된_로트_몫은_30일짜리_새_로트가_된다() {
        val (lot300, lot500, _) = threeLots()
        service.use(memberId, 100, 600)
        advanceDays(6)

        assertTrue(service.refund(memberId, 100, PointRefType.ORDER, 100, 600))

        assertEquals(300, repo.lot(lot300).remainingAmount)
        assertEquals(0, repo.lot(lot500).remainingAmount)
        val newLot = repo.lots.values.single { it.sourceType == PointSourceType.REFUND }
        assertEquals(500, newLot.amount)
        assertEquals(500, newLot.remainingAmount)
        assertEquals(100L, newLot.sourceId)
        assertEquals(start.plusDays(6).plusDays(30), newLot.expiresAt)
        assertEquals(1_200, repo.balance(memberId))
        assertEquals(repo.remainingSum(memberId), repo.balance(memberId))
    }

    @Test
    fun 같은_항목_되돌림을_두_번_실행해도_원장_한_건이다() {
        grant(300, 10, 1)
        service.use(memberId, 100, 200)

        assertTrue(service.refund(memberId, 100, PointRefType.ORDER_ITEM, 11, 50))
        assertFalse(service.refund(memberId, 100, PointRefType.ORDER_ITEM, 11, 50))
        assertFalse(service.refund(memberId, 100, PointRefType.ORDER_ITEM, 12, 0))

        assertEquals(1, repo.transactions.values.count { it.type == PointTransactionType.REFUND })
        assertEquals(150, repo.balance(memberId))
    }

    @Test
    fun 만료_작업은_원장_EXPIRE와_잔액_감소를_한_번만_한다() {
        val lot = grant(300, 5, 1)
        grant(100, 20, 2)
        assertFalse(service.expireLot(memberId, lot))
        advanceDays(6)

        assertTrue(service.expireLot(memberId, lot))
        assertFalse(service.expireLot(memberId, lot))

        assertEquals(0, repo.lot(lot).remainingAmount)
        assertEquals(100, repo.balance(memberId))
        val expire = repo.transactions.values.single { it.type == PointTransactionType.EXPIRE }
        assertEquals(-300, expire.amount)
        assertEquals(100, expire.balanceAfter)
    }
}
