package com.dogyun.kairos.payment.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaymentServiceTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 11, 10, 12, 0)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)
    private val repository = FakePaymentRepository()
    private val service = PaymentService(repository, clock)

    @Test
    fun 다른_주문이_쓴_paymentKey로_start하면_PAYMENT_KEY_DUPLICATED() {
        service.prepare(1L, "ORD-1", 10_000L)
        service.prepare(2L, "ORD-2", 20_000L)
        service.start(1L, "key-1")
        val e = assertFailsWith<DomainException> { service.start(2L, "key-1") }
        assertEquals(ErrorCode.PAYMENT_KEY_DUPLICATED, e.errorCode)
    }

    @Test
    fun findStale은_정체된_IN_PROGRESS와_모든_UNKNOWN을_돌려준다() {
        // 정체된 IN_PROGRESS: 10분 전에 시작
        val older = Clock.fixed(now.minusMinutes(10).atZone(zone).toInstant(), zone)
        val oldService = PaymentService(repository, older)
        oldService.prepare(1L, "ORD-1", 1_000L); oldService.start(1L, "k1")
        // UNKNOWN: 방금 시작했어도 포함
        service.prepare(2L, "ORD-2", 1_000L); service.start(2L, "k2"); service.markUnknown(2L)
        // 최근 IN_PROGRESS: 제외
        service.prepare(3L, "ORD-3", 1_000L); service.start(3L, "k3")
        // READY, APPROVED: 제외
        service.prepare(4L, "ORD-4", 1_000L)
        service.prepare(5L, "ORD-5", 1_000L); service.start(5L, "k5"); service.approve(5L)

        val stale = service.findStale(now.minusMinutes(5), 100)
        assertEquals(setOf(1L, 2L), stale.map { it.orderId }.toSet())
    }

    @Test
    fun findVoidCandidates는_키가_있는_FAILED_UNKNOWN만_id_순_keyset으로_돌려주고_markVoided하면_빠진다() {
        service.prepare(1L, "ORD-1", 1_000L); service.start(1L, "k1"); service.fail(1L, "x")
        service.prepare(2L, "ORD-2", 1_000L); service.fail(2L, "만료") // 키 없음
        service.prepare(3L, "ORD-3", 1_000L); service.start(3L, "k3"); service.markUnknown(3L)
        service.prepare(4L, "ORD-4", 1_000L); service.start(4L, "k4") // IN_PROGRESS

        val first = service.findVoidCandidates(0, 1)
        assertEquals(listOf(1L), first.map { it.orderId })
        assertEquals(listOf(3L), service.findVoidCandidates(first.last().paymentId, 10).map { it.orderId })

        service.markVoided(1L)
        assertEquals(listOf(3L), service.findVoidCandidates(0, 10).map { it.orderId })
    }

    @Test
    fun 없는_주문의_결제는_PAYMENT_NOT_FOUND() {
        assertEquals(ErrorCode.PAYMENT_NOT_FOUND, assertFailsWith<DomainException> { service.get(9L) }.errorCode)
    }
}
