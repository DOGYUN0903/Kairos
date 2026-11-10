package com.dogyun.kairos.benefit.application

import com.dogyun.kairos.benefit.domain.PointRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime

/** 만료된 포인트 로트를 건별로 정리한다. 이 서비스는 트랜잭션을 열지 않는다(건별 트랜잭션은 [PointService.expireLot]). */
@Service
class PointExpiryService(
    private val pointService: PointService,
    private val pointRepository: PointRepository,
    private val clock: Clock,
) {

    /** 실제로 만료 처리한 로트 수. 한 건이 실패해도 다음 건을 계속한다. */
    fun expireLots(limit: Int = 100): Int {
        val now = LocalDateTime.now(clock)
        var afterId = 0L
        var handled = 0
        while (true) {
            val page = pointRepository.findExpiredLots(now, afterId, limit)
            for ((memberId, lotId) in page) {
                try {
                    if (pointService.expireLot(memberId, lotId)) handled++
                } catch (e: RuntimeException) {
                    log.error("포인트 로트 만료에 실패했다: memberId={}, lotId={}", memberId, lotId, e)
                }
            }
            if (page.size < limit) return handled
            afterId = page.last().second
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(PointExpiryService::class.java)
    }
}
