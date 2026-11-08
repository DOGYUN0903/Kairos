package com.dogyun.kairos.payment.infrastructure

import com.dogyun.kairos.payment.application.PaymentMetrics
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/** `kairos.payment.duration` Timer. 백분위 히스토그램을 켜서 Grafana 에서 p95/p99 를 계산할 수 있게 한다. */
@Component
class MicrometerPaymentMetrics(private val registry: MeterRegistry) : PaymentMetrics {

    override fun recordDuration(result: String, nanos: Long) {
        Timer.builder("kairos.payment.duration")
            .description("Mock PG 호출 소요 시간")
            .tag("result", result)
            .publishPercentileHistogram()
            .register(registry)
            .record(nanos, TimeUnit.NANOSECONDS)
    }
}
