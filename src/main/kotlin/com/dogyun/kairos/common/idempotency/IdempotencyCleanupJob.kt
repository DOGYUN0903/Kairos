package com.dogyun.kairos.common.idempotency

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

/** 보관 기간이 지난 멱등 키를 지운다. */
@Component
@ConditionalOnProperty("kairos.jobs.enabled", havingValue = "true", matchIfMissing = true)
class IdempotencyCleanupJob(
    private val store: IdempotencyStore,
    private val clock: Clock,
) {

    @Scheduled(fixedDelayString = "\${kairos.jobs.idempotency-cleanup-delay:1h}")
    fun purgeExpired() {
        try {
            val deleted = store.deleteExpired(LocalDateTime.now(clock))
            if (deleted > 0) log.info { "만료된 멱등 키 ${deleted}건을 지웠다" }
        } catch (e: RuntimeException) {
            log.error(e) { "멱등 키 정리가 실패했다" }
        }
    }
}
