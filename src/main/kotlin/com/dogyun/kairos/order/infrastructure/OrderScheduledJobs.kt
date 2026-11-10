package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.order.application.DeliveryTrackingService
import com.dogyun.kairos.order.application.OrderExpiryService
import com.dogyun.kairos.order.application.PaymentReconciliationService
import com.dogyun.kairos.order.application.PurchaseAutoConfirmService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** 주문 정리 작업 스케줄. 이 클래스는 트랜잭션을 열지 않는다(서비스가 건별 트랜잭션을 연다). 예외는 로그만 남기고 삼킨다. */
@Component
@ConditionalOnProperty("kairos.jobs.enabled", havingValue = "true", matchIfMissing = true)
class OrderScheduledJobs(
    private val expiryService: OrderExpiryService,
    private val reconciliationService: PaymentReconciliationService,
    private val deliveryTrackingService: DeliveryTrackingService,
    private val purchaseAutoConfirmService: PurchaseAutoConfirmService,
) {

    @Scheduled(fixedDelayString = "\${kairos.jobs.order-expiry-delay:60s}")
    fun expirePendingOrders() = guarded("결제 대기 만료") { expiryService.expirePendingOrders() }

    @Scheduled(fixedDelayString = "\${kairos.jobs.payment-reconcile-delay:60s}")
    fun reconcilePayments() {
        guarded("결제 승인 대사") { reconciliationService.reconcileApprovals() }
        guarded("닫힌 주문 재취소") { reconciliationService.revoidClosedOrders() }
        guarded("환불 대사") { reconciliationService.reconcileCancels() }
    }

    @Scheduled(fixedDelayString = "\${kairos.jobs.delivery-poll-delay:10m}")
    fun pollShipping() = guarded("배송 조회") { deliveryTrackingService.pollShipping() }

    @Scheduled(fixedDelayString = "\${kairos.jobs.auto-confirm-delay:1h}")
    fun confirmDelivered() = guarded("자동 구매 확정") { purchaseAutoConfirmService.confirmDelivered() }

    private fun guarded(name: String, job: () -> Int) {
        try {
            val count = job()
            if (count > 0) log.info("{} 작업: {}건 처리", name, count)
        } catch (e: RuntimeException) {
            log.error("{} 작업이 실패했다", name, e)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(OrderScheduledJobs::class.java)
    }
}
