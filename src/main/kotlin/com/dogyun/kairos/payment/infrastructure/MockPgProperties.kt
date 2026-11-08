package com.dogyun.kairos.payment.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("kairos.mock-pg")
data class MockPgProperties(
    val delay: Duration = Duration.ofMillis(100),
    val failureRate: Double = 0.0,
    val timeout: Duration = Duration.ofSeconds(3),
)
