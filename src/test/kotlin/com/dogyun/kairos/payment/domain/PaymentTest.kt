package com.dogyun.kairos.payment.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class PaymentTest {

    private val now = LocalDateTime.of(2026, 11, 10, 12, 0)

    private fun ready() = Payment.ready(1L, "ORD-1", 10_000L)

    private fun approved() = ready().apply { start("key-1", now); approve(now) }

    private fun assertInvalid(block: () -> Unit) {
        assertEquals(ErrorCode.PAYMENT_INVALID_STATE_TRANSITION, assertFailsWith<DomainException> { block() }.errorCode)
    }

    @Test
    fun READY에서_start하면_IN_PROGRESS이고_paymentKey가_저장된다() {
        val payment = ready()
        payment.start("key-1", now)
        assertEquals(PaymentStatus.IN_PROGRESS, payment.status)
        assertEquals("key-1", payment.paymentKey)
        assertEquals(now, payment.inProgressAt)
    }

    @Test
    fun APPROVED나_FAILED에서_start는_INVALID_STATE_TRANSITION() {
        assertInvalid { approved().start("key-2", now) }
        assertInvalid { ready().apply { fail("거절") }.start("key-2", now) }
    }

    @Test
    fun voidAfterApproval은_IN_PROGRESS_UNKNOWN에서만_CANCELED_전액_환불이다() {
        val inProgress = ready().apply { start("k", now) }
        inProgress.voidAfterApproval()
        assertEquals(PaymentStatus.CANCELED, inProgress.status)
        assertEquals(10_000L, inProgress.refundedAmount)
        assertEquals(0L, inProgress.balance)

        val unknown = ready().apply { start("k", now); markUnknown() }
        unknown.voidAfterApproval()
        assertEquals(PaymentStatus.CANCELED, unknown.status)

        assertInvalid { ready().voidAfterApproval() }
        assertInvalid { approved().voidAfterApproval() }
    }

    @Test
    fun 부분_환불_누적이_금액과_같아지면_CANCELED_아니면_PARTIAL_CANCELED() {
        val payment = approved()
        payment.refund(3_000L)
        assertEquals(PaymentStatus.PARTIAL_CANCELED, payment.status)
        assertEquals(7_000L, payment.balance)
        payment.refund(7_000L)
        assertEquals(PaymentStatus.CANCELED, payment.status)
        assertEquals(10_000L, payment.refundedAmount)
    }

    @Test
    fun 누적_환불이_금액을_넘으면_INVALID_STATE_TRANSITION() {
        val payment = approved().apply { refund(4_000L) }
        assertInvalid { payment.refund(6_001L) }
        assertEquals(4_000L, payment.refundedAmount)
        assertInvalid { ready().refund(1L) }
    }

    @Test
    fun UNKNOWN에서_approve와_fail_모두_가능하다() {
        val a = ready().apply { start("k", now); markUnknown(); approve(now) }
        assertEquals(PaymentStatus.APPROVED, a.status)
        assertNotNull(a.approvedAt)
        val f = ready().apply { start("k", now); markUnknown(); fail("PG 거절") }
        assertEquals(PaymentStatus.FAILED, f.status)
        assertEquals("PG 거절", f.failReason)
    }

    @Test
    fun markVoided는_키가_있는_FAILED_UNKNOWN_결제를_환불액_변경_없이_CANCELED로_닫는다() {
        val failed = ready().apply { start("k", now); fail("x") }.apply { markVoided() }
        assertEquals(PaymentStatus.CANCELED, failed.status)
        assertEquals(0L, failed.refundedAmount)
        assertEquals(PaymentStatus.CANCELED, ready().apply { start("k", now); markUnknown() }.apply { markVoided() }.status)
    }

    @Test
    fun markVoided는_키가_없거나_다른_상태면_INVALID_STATE_TRANSITION() {
        assertInvalid { ready().apply { fail("만료") }.markVoided() } // payment_key 없음
        assertInvalid { approved().markVoided() }
        assertInvalid { ready().apply { start("k", now) }.markVoided() }
    }
}
