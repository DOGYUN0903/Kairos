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
}
