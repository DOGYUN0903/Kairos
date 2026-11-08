package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.order.application.PlaceOutcome
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

class MicrometerOrderMetricsTest {

    private val registry = SimpleMeterRegistry()
    private val metrics = MicrometerOrderMetrics(registry)

    private fun gauge(name: String, dealId: Long) = registry.get(name).tag("dealId", dealId.toString()).gauge().value()

    @Test
    fun 주문_결과가_result_태그_카운터로_기록된다() {
        metrics.recordPlaced(PlaceOutcome.PAID)
        metrics.recordPlaced(PlaceOutcome.PAID)
        metrics.recordPlaced(PlaceOutcome.SOLD_OUT)

        assertEquals(2.0, registry.get("kairos.order.placed").tag("result", "PAID").counter().count())
        assertEquals(1.0, registry.get("kairos.order.placed").tag("result", "SOLD_OUT").counter().count())
    }

    @Test
    fun 순판매량이_총수량을_넘으면_oversold가_차이를_보인다() {
        metrics.registerDeal(1L, 10)
        metrics.addSold(1L, 6)
        metrics.addSold(1L, 6)

        assertEquals(10.0, gauge("kairos.deal.total", 1L))
        assertEquals(12.0, gauge("kairos.deal.sold", 1L))
        assertEquals(2.0, gauge("kairos.deal.oversold", 1L))
    }

    @Test
    fun 총수량_이내면_oversold는_0이고_취소는_차감된다() {
        metrics.registerDeal(2L, 10)
        metrics.addSold(2L, 4)
        metrics.subtractSold(2L, 1)

        assertEquals(3.0, gauge("kairos.deal.sold", 2L))
        assertEquals(0.0, gauge("kairos.deal.oversold", 2L))
    }

    @Test
    fun 같은_딜을_여러번_등록해도_게이지는_하나다() {
        metrics.registerDeal(3L, 10)
        metrics.registerDeal(3L, 10)
        metrics.addSold(3L, 1)

        assertEquals(1, registry.find("kairos.deal.sold").tag("dealId", "3").gauges().size)
        assertEquals(1, registry.find("kairos.deal.total").tag("dealId", "3").gauges().size)
        assertEquals(1, registry.find("kairos.deal.oversold").tag("dealId", "3").gauges().size)
    }
}
