package com.dogyun.kairos.payment.infrastructure

import com.dogyun.kairos.payment.application.PgClient
import com.dogyun.kairos.payment.application.PgResponse
import org.springframework.stereotype.Component
import java.util.random.RandomGenerator

/** 호출 스레드를 의도적으로 블로킹한다(Phase 2 부하 측정 대상). 비동기로 바꾸지 않는다. */
@Component
class MockPgClient(
    private val properties: MockPgProperties,
    private val random: RandomGenerator,
) : PgClient {

    override fun approve(orderId: Long, amount: Long): PgResponse {
        val timedOut = properties.delay > properties.timeout
        try {
            Thread.sleep(minOf(properties.delay, properties.timeout))
        } catch (e: InterruptedException) {
            // checked 예외는 Spring이 롤백하지 않으므로, 인터럽트 상태를 복원하고 unchecked로 던져 롤백시킨다.
            Thread.currentThread().interrupt()
            throw IllegalStateException("Mock PG 호출이 인터럽트되었습니다.", e)
        }
        return when {
            timedOut -> PgResponse.TIMEOUT
            random.nextDouble() < properties.failureRate -> PgResponse.FAILED
            else -> PgResponse.APPROVED
        }
    }
}
