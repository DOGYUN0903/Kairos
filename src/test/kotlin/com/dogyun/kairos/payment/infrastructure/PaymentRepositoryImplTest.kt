package com.dogyun.kairos.payment.infrastructure

import com.dogyun.kairos.IntegrationTestSupport
import com.dogyun.kairos.payment.domain.Payment
import com.dogyun.kairos.payment.domain.PaymentRepository
import com.dogyun.kairos.payment.domain.PaymentStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PaymentRepositoryImplTest : IntegrationTestSupport() {

    @Autowired
    private lateinit var repository: PaymentRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeTest
    fun cleanUp() {
        jdbcTemplate.update("delete from payment")
    }

    @Test
    fun 실패_기록과_환불_상태변경이_저장된다() {
        val failed = repository.save(Payment.failed(1L, 100L, "PAYMENT_TIMEOUT"))
        assertEquals("PAYMENT_TIMEOUT", repository.findLatestByOrderId(1L)?.failReason)
        assertEquals(PaymentStatus.FAILED, failed.status)

        val approved = repository.save(Payment.approved(2L, 200L))
        approved.refund()
        repository.save(approved)
        val found = assertNotNull(repository.findLatestByOrderId(2L))
        assertEquals(PaymentStatus.REFUNDED, found.status)
        assertEquals(approved.id, found.id)
    }
}
