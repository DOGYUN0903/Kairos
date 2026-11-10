package com.dogyun.kairos.benefit.infrastructure

import com.dogyun.kairos.benefit.application.PointExpiryService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** 혜택 정리 작업 스케줄. 트랜잭션을 열지 않는다(서비스가 건별 트랜잭션을 연다). 예외는 로그만 남기고 삼킨다. */
@Component
@ConditionalOnProperty("kairos.jobs.enabled", havingValue = "true", matchIfMissing = true)
class BenefitScheduledJobs(private val pointExpiryService: PointExpiryService) {

    @Scheduled(fixedDelayString = "\${kairos.jobs.point-expiry-delay:1h}")
    fun expirePoints() {
        try {
            val count = pointExpiryService.expireLots()
            if (count > 0) log.info("포인트 만료 작업: {}건 처리", count)
        } catch (e: RuntimeException) {
            log.error("포인트 만료 작업이 실패했다", e)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(BenefitScheduledJobs::class.java)
    }
}
