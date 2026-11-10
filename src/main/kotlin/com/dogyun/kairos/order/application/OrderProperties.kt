package com.dogyun.kairos.order.application

import com.dogyun.kairos.order.domain.ReturnFeePolicy
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
    val returnWindow: Duration = Duration.ofDays(7),
    val returnAutoApproveAfter: Duration = Duration.ofDays(3),
    val returnShippingFee: Long = 3_000,
) {
    fun shippingPolicy() = ShippingPolicy(freeShippingThreshold, baseShippingFee)

    fun returnFeePolicy() = ReturnFeePolicy(returnShippingFee, baseShippingFee)

    /** 반품 가능 기간과 자동 구매 확정 기간이 다르면 열린 반품이 있는 채로 확정 시각이 먼저 올 수 있어 기동 시 경고한다. */
    fun returnWindowMismatch(): Boolean = returnWindow != autoConfirmAfter
}
