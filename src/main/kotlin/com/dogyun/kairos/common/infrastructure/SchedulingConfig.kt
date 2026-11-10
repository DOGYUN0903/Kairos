package com.dogyun.kairos.common.infrastructure

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** 정리 작업 스케줄러. 테스트는 `kairos.jobs.enabled=false` 로 끄고 작업 메서드를 직접 호출한다. */
@Configuration
@EnableScheduling
@ConditionalOnProperty("kairos.jobs.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig
