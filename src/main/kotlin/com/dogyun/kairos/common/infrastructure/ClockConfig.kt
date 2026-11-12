package com.dogyun.kairos.common.infrastructure

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.time.Clock
import java.time.ZoneId

/** 시스템 시계. demo 프로필에서는 스레드별로 시각을 옮길 수 있는 `DemoShiftClock` 이 대신 `Clock` 빈이 된다. */
@Configuration
class ClockConfig {
    @Bean
    @Profile("!demo")
    fun clock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))
}
