package com.dogyun.kairos.payment.application

import com.dogyun.kairos.payment.domain.Payment
import com.dogyun.kairos.payment.domain.PaymentRepository
import com.dogyun.kairos.payment.domain.PaymentStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentServiceTest {

    private class FakePgClient(var response: PgResponse) : PgClient {
        override fun approve(orderId: Long, amount: Long) = response
    }

    private class FakePaymentRepository : PaymentRepository {
        val store = mutableListOf<Payment>()
        override fun save(payment: Payment): Payment {
            val saved = if (payment.id == null) Payment.restore(store.size + 1L, payment.orderId, payment.amount, payment.status, payment.failReason) else payment
            store.removeAll { it.id == saved.id }
            store += saved
            return saved
        }
        override fun findLatestByOrderId(orderId: Long) = store.lastOrNull { it.orderId == orderId }
    }

    private class FakePaymentMetrics : PaymentMetrics {
        val recorded = mutableListOf<Pair<String, Long>>()
        override fun recordDuration(result: String, nanos: Long) {
            recorded += result to nanos
        }
    }

    private val pg = FakePgClient(PgResponse.APPROVED)
    private val repository = FakePaymentRepository()
    private val metrics = FakePaymentMetrics()
    private val service = PaymentService(pg, repository, metrics)

    @Test
    fun 승인되면_APPROVED로_저장() {
        val result = service.pay(10L, 5000L)
        val approved = assertIs<PayResult.Approved>(result)
        val saved = repository.store.single()
        assertEquals(approved.paymentId, saved.id)
        assertEquals(PaymentStatus.APPROVED, saved.status)
        assertEquals(5000L, saved.amount)
        assertNull(saved.failReason)
    }

    @Test
    fun 실패해도_FAILED로_기록된다() {
        pg.response = PgResponse.FAILED
        assertEquals(PayResult.Failed(FailReason.PAYMENT_FAILED), service.pay(10L, 5000L))
        val saved = repository.store.single()
        assertEquals(PaymentStatus.FAILED, saved.status)
        assertEquals("PAYMENT_FAILED", saved.failReason)
    }

    @Test
    fun 타임아웃도_FAILED로_기록된다() {
        pg.response = PgResponse.TIMEOUT
        assertEquals(PayResult.Failed(FailReason.PAYMENT_TIMEOUT), service.pay(10L, 5000L))
        assertEquals("PAYMENT_TIMEOUT", repository.store.single().failReason)
    }

    @Test
    fun 환불하면_REFUNDED() {
        service.pay(10L, 5000L)
        service.refund(10L)
        assertEquals(PaymentStatus.REFUNDED, repository.store.single().status)
    }

    @Test
    fun 승인되지_않은_주문은_환불할_수_없다() {
        pg.response = PgResponse.FAILED
        service.pay(10L, 5000L)
        assertFailsWith<IllegalStateException> { service.refund(10L) }
        assertFailsWith<IllegalStateException> { service.refund(999L) }
    }

    @Test
    fun 결제_소요시간이_결과별로_기록된다() {
        service.pay(10L, 5000L)
        pg.response = PgResponse.FAILED
        service.pay(11L, 5000L)
        pg.response = PgResponse.TIMEOUT
        service.pay(12L, 5000L)

        assertEquals(listOf("APPROVED", "FAILED", "TIMEOUT"), metrics.recorded.map { it.first })
        assertTrue(metrics.recorded.all { it.second >= 0 })
    }
}
