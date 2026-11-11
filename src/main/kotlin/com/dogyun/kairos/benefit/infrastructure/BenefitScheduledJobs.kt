package com.dogyun.kairos.benefit.infrastructure

import com.dogyun.kairos.benefit.application.PointExpiryService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

/** 혜택 정리 작업 스케줄. 트랜잭션을 열지 않는다(서비스가 건별 트랜잭션을 연다). 예외는 로그만 남기고 삼킨다. */
@Component
@ConditionalOnProperty("kairos.jobs.enabled", havingValue = "true", matchIfMissing = true)
class BenefitScheduledJobs(private val pointExpiryService: PointExpiryService) {

    @Scheduled(fixedDelayString = "\${kairos.jobs.point-expiry-delay:1h}")
    fun expirePoints() {
        try {
            val count = pointExpiryService.expireLots()
            if (count > 0) log.info { "포인트 만료 작업: ${count}건 처리" }
        } catch (e: RuntimeException) {
            log.error(e) { "포인트 만료 작업이 실패했다" }
        }
    }
}
