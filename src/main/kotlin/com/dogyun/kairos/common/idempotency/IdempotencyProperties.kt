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
        "/api/v1/cart/items",
        "/api/v1/payments/confirm",
        "/api/v1/orders/*/items/*/cancel",
        "/api/v1/seller/orders/*/items/*/cancel",
        "/api/v1/admin/orders/*/items/*/cancel",
        "/api/v1/coupons/policies/*/claim",
        "/api/v1/seller/coupons/policies",
        "/api/v1/admin/coupons/policies",
        "/api/v1/orders/*/items/*/reviews",
        "/api/v1/orders/*/items/*/returns",
        "/api/v1/admin/settlements/generate",
        "/api/v1/admin/sellers/*/settlement-adjustments",
        "/api/v1/admin/categories",
    ),
)
