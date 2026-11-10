package com.dogyun.kairos.payment.infrastructure

import com.dogyun.kairos.CommerceTestSupport
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.payment.domain.Payment
import com.dogyun.kairos.payment.domain.PaymentRepository
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentRepositoryImplTest : CommerceTestSupport() {

    @Autowired
    private lateinit var repository: PaymentRepository

    private fun started(orderId: Long, key: String) =
        Payment.ready(orderId, "ORD-$orderId", 10_000L).apply { start(key, LocalDateTime.of(2026, 11, 10, 12, 0)) }

    @Test
    fun 같은_paymentKey_두_번_저장은_PAYMENT_KEY_DUPLICATED() {
        repository.save(started(1L, "dup-key"))
        val e = org.junit.jupiter.api.assertThrows<DomainException> { repository.save(started(2L, "dup-key")) }
        assertEquals(ErrorCode.PAYMENT_KEY_DUPLICATED, e.errorCode)
    }

    @Test
    fun findVoidCandidates는_키가_있는_FAILED_UNKNOWN만_id_순_keyset으로_돌려준다() {
        val failed = repository.save(started(901L, "vc-1").apply { fail("x") })
        repository.save(Payment.ready(902L, "ORD-902", 10_000L).apply { fail("만료") }) // 키 없음
        val unknown = repository.save(started(903L, "vc-3").apply { markUnknown() })
        repository.save(started(904L, "vc-4")) // IN_PROGRESS

        val mine = setOf(failed.id, unknown.id)
        val first = repository.findVoidCandidates(failed.id!! - 1, 1)
        assertEquals(listOf(failed.id), first.map { it.id })
        val rest = repository.findVoidCandidates(first.last().id!!, 100).map { it.id }.filter { it in mine }
        assertEquals(listOf(unknown.id), rest)
    }
}
