package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.application.sweepByKeyset
import com.dogyun.kairos.order.domain.OrderRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

/**
 * 판매자가 [OrderProperties.returnAutoApproveAfter] 동안 응답하지 않은 반품을 시스템이 승인한다.
 * 훑기 쿼리는 후보만 고르고, 승인 여부는 [OrderReturnService.approveBySystem] 이 주문 락 안에서 상태를 다시 보고 정한다.
 */
@Service
class ReturnAutoApproveService(
    private val orderRepository: OrderRepository,
    private val orderReturnService: OrderReturnService,
    private val properties: OrderProperties,
    private val clock: Clock,
) {

    /** 실제로 승인한 반품 수(그 사이 판매자가 처리한 건은 세지 않는다). */
    fun approveStale(limit: Int = 100): Int {
        val threshold = LocalDateTime.now(clock).minus(properties.returnAutoApproveAfter)
        return sweepByKeyset(limit, { it.second }, { after -> orderRepository.findReturnIdsRequestedBefore(threshold, after, limit) }) { (orderId, returnId) ->
            try {
                orderReturnService.approveBySystem(orderId, returnId)
            } catch (e: RuntimeException) {
                log.warn(e) { "반품 자동 승인에 실패했다: orderId=$orderId, returnId=$returnId" }
                false
            }
        }
    }
}
