package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.order.application.OrderMetrics
import com.dogyun.kairos.order.application.PlaceOutcome
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Micrometer 구현. 딜별 sold/total 을 메모리(AtomicLong)에 누적하고 Gauge 로 노출한다.
 * - 순판매량은 DB 에서 계산하지 않는다: 락이 없으면 lost update 로 남은 수량이 실제보다 *많이* 남아
 *   `총수량 − 남은 수량`으로는 초과 판매가 드러나지 않기 때문이다. 커밋된 PAID 수량을 직접 세어야 한다.
 * - 앱 재시작 시 0 부터 시작한다(측정 단위: 새 딜 하나에 대한 부하 테스트 1회).
 * - `dealId` 태그 카디널리티 주의: 측정용 딜이 소수라는 전제다. 딜이 많아지면 시계열이 딜 수만큼 늘어난다.
 */
@Component
class MicrometerOrderMetrics(private val registry: MeterRegistry) : OrderMetrics {

    private class DealCounters(total: Int) {
        val sold = AtomicLong()
        val total = AtomicLong(total.toLong())
    }

    private val deals = ConcurrentHashMap<Long, DealCounters>()

    override fun recordPlaced(result: PlaceOutcome) {
        registry.counter(ORDER_PLACED, "result", result.name).increment()
    }

    override fun registerDeal(dealId: Long, totalQuantity: Int) {
        counters(dealId, totalQuantity).total.set(totalQuantity.toLong())
    }

    override fun addSold(dealId: Long, quantity: Int) {
        counters(dealId, 0).sold.addAndGet(quantity.toLong())
    }

    override fun subtractSold(dealId: Long, quantity: Int) {
        counters(dealId, 0).sold.addAndGet(-quantity.toLong())
    }

    /** 딜당 1회만 게이지를 등록한다(computeIfAbsent 로 동시 호출에도 한 번). */
    private fun counters(dealId: Long, totalQuantity: Int): DealCounters =
        deals.computeIfAbsent(dealId) {
            val c = DealCounters(totalQuantity)
            val tag = dealId.toString()
            Gauge.builder(DEAL_SOLD, c) { it.sold.get().toDouble() }.tag("dealId", tag).register(registry)
            Gauge.builder(DEAL_TOTAL, c) { it.total.get().toDouble() }.tag("dealId", tag).register(registry)
            Gauge.builder(DEAL_OVERSOLD, c) { maxOf(0L, it.sold.get() - it.total.get()).toDouble() }.tag("dealId", tag).register(registry)
            c
        }

    private companion object {
        const val ORDER_PLACED = "kairos.order.placed"
        const val DEAL_SOLD = "kairos.deal.sold"
        // 주의: Prometheus 노출 시 `_total` 은 카운터 예약 접미사라 떼어져 `kairos_deal{dealId=...}` 로 보인다.
        const val DEAL_TOTAL = "kairos.deal.total"
        const val DEAL_OVERSOLD = "kairos.deal.oversold"
    }
}
