package com.dogyun.kairos.settlement.infrastructure

import com.dogyun.kairos.settlement.application.SettlementGenerationService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** 정산 생성 스케줄. 이 클래스는 트랜잭션을 열지 않는다(서비스가 판매자별 트랜잭션을 연다). 예외는 로그만 남기고 삼킨다. */
@Component
@ConditionalOnProperty("kairos.jobs.enabled", havingValue = "true", matchIfMissing = true)
class SettlementScheduledJobs(private val generationService: SettlementGenerationService) {

    @Scheduled(cron = "\${kairos.jobs.settlement-generate-cron:0 0 3 * * MON}", zone = "Asia/Seoul")
    fun generateSettlements() = guarded("주간 정산 생성") { generationService.generateDue() }

    private fun guarded(name: String, job: () -> Int) {
        try {
            val count = job()
            if (count > 0) log.info("{} 작업: {}건 처리", name, count)
        } catch (e: RuntimeException) {
            log.error("{} 작업이 실패했다", name, e)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(SettlementScheduledJobs::class.java)
    }
}
