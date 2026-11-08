package com.dogyun.kairos.payment.infrastructure

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class MicrometerPaymentMetricsTest {

    private val registry = SimpleMeterRegistry()
    private val metrics = MicrometerPaymentMetrics(registry)

    @Test
    fun 결제_소요시간이_결과별_타이머에_기록된다() {
        metrics.recordDuration("APPROVED", TimeUnit.MILLISECONDS.toNanos(20))
        metrics.recordDuration("APPROVED", TimeUnit.MILLISECONDS.toNanos(40))
        metrics.recordDuration("TIMEOUT", TimeUnit.MILLISECONDS.toNanos(300))

        val approved = registry.get("kairos.payment.duration").tag("result", "APPROVED").timer()
        assertEquals(2L, approved.count())
        assertEquals(60.0, approved.totalTime(TimeUnit.MILLISECONDS))
        assertEquals(1L, registry.get("kairos.payment.duration").tag("result", "TIMEOUT").timer().count())
    }
}
