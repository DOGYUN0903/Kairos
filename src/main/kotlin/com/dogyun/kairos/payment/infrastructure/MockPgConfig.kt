package com.dogyun.kairos.payment.infrastructure

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ThreadLocalRandom
import java.util.random.RandomGenerator

@Configuration
class MockPgConfig {
    @Bean
    fun mockPgRandom(): RandomGenerator = ThreadSafeRandom

    /** 스레드마다 ThreadLocalRandom에 위임한다(Phase 2 동시 부하에서 안전). 테스트는 시드 고정 Random을 직접 주입한다. */
    private object ThreadSafeRandom : RandomGenerator {
        override fun nextLong(): Long = ThreadLocalRandom.current().nextLong()
        override fun nextDouble(): Double = ThreadLocalRandom.current().nextDouble()
    }
}
