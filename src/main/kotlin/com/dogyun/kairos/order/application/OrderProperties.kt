package com.dogyun.kairos.order.application

import com.dogyun.kairos.order.domain.ShippingPolicy
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("kairos.order")
data class OrderProperties(
    val paymentTtl: Duration = Duration.ofMinutes(15),
    val freeShippingThreshold: Long = 50_000,
    val baseShippingFee: Long = 3_000,
    val autoConfirmAfter: Duration = Duration.ofDays(7),
    val inProgressTimeout: Duration = Duration.ofMinutes(5),
    val cancelRequestTimeout: Duration = Duration.ofMinutes(1),
) {
    fun shippingPolicy() = ShippingPolicy(freeShippingThreshold, baseShippingFee)
}
