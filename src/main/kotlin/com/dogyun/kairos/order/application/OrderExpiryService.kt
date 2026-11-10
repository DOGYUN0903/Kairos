package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.application.sweepByKeyset
import com.dogyun.kairos.common.application.TransactionRunner
import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.OrderStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime

/**
 * 결제 대기 시간이 지난 주문을 닫는다. 대상 id 는 트랜잭션 밖에서 찾고, 건별 트랜잭션이 주문 행을 먼저 잠근 뒤 현재 상태를 다시 확인한다.
 * 결제가 `READY`(PG 승인을 시작하지 않음)일 때만 만료한다. `IN_PROGRESS`/`UNKNOWN` 은 PG 결과를 모르므로 승인 대사가 맡는다.
 * 이 클래스에는 `@Transactional` 을 붙이지 않는다.
 */
@Service
class OrderExpiryService(
    private val orderRepository: OrderRepository,
    private val paymentGateway: PaymentGateway,
    private val stockManager: StockManager,
    private val benefitReverter: BenefitReverter,
    private val tx: TransactionRunner,
    private val clock: Clock,
) {

    /** 만료시킨 주문 수. [limit] 은 keyset 페이지 크기다. */
    fun expirePendingOrders(limit: Int = 100): Int {
        val now = LocalDateTime.now(clock)
        return sweepByKeyset(limit, { it }, { after -> orderRepository.findExpiredPendingIds(now, after, limit) }) { orderId ->
            try {
                expire(orderId)
            } catch (e: RuntimeException) {
                log.error("주문 만료 처리에 실패했다: orderId={}", orderId, e)
                false
            }
        }
    }

    private fun expire(orderId: Long): Boolean = tx.run {
        val order = orderRepository.findByIdForUpdate(orderId) ?: return@run false
        if (order.status != OrderStatus.PENDING_PAYMENT) return@run false
        if (paymentGateway.get(orderId).status != "READY") return@run false
        paymentGateway.fail(orderId, "EXPIRED")
        order.expire()
        orderRepository.save(order)
        // 쿠폰·포인트를 재고보다 먼저 되돌린다(락 순서: orders → member_point → coupon → sku).
        benefitReverter.revertAll(orderId, order.memberId, order.usedCouponIds(), order.pointsUsed)
        stockManager.restore(order.skuQuantities.map { (skuId, quantity) -> SkuQuantity(skuId, quantity) })
        true
    }

    private companion object {
        val log = LoggerFactory.getLogger(OrderExpiryService::class.java)
    }
}
