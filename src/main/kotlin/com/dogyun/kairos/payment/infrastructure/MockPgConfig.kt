package com.dogyun.kairos.payment.infrastructure

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.random.RandomGenerator

@Configuration
class MockPgConfig {
    @Bean
    fun mockPgRandom(): RandomGenerator = RandomGenerator.getDefault()
}
