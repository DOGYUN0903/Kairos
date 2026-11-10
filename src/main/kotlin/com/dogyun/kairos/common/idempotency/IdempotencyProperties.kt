package com.dogyun.kairos.common.idempotency

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** Idempotency-Key 를 요구하는 POST 경로(AntPathMatcher 패턴)와 보관·정체 판정 시간. */
@ConfigurationProperties("kairos.idempotency")
data class IdempotencyProperties(
    val ttl: Duration = Duration.ofHours(24),
    val inProgressTimeout: Duration = Duration.ofSeconds(60),
    val paths: List<String> = listOf(
        "/api/v1/orders",
        "/api/v1/payments/confirm",
        "/api/v1/orders/*/items/*/cancel",
        "/api/v1/seller/orders/*/items/*/cancel",
        "/api/v1/admin/orders/*/items/*/cancel",
    ),
)
