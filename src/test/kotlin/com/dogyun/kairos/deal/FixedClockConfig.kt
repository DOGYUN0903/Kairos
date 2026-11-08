package com.dogyun.kairos.deal

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** 테스트 기준 시각을 2026-12-08T12:00 (Asia/Seoul)로 고정한다. */
@TestConfiguration
class FixedClockConfig {
    @Bean
    @Primary
    fun fixedClock(): Clock = Clock.fixed(Instant.parse("2026-12-08T03:00:00Z"), ZoneId.of("Asia/Seoul"))
}
