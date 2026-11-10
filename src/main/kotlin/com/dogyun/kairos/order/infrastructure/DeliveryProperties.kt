package com.dogyun.kairos.order.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("kairos.delivery")
data class DeliveryProperties(
    val provider: String = "mock",
    val sweetTracker: SweetTracker = SweetTracker(),
) {
    data class SweetTracker(
        val baseUrl: String = "https://info.sweettracker.co.kr",
        val apiKey: String = "",
        val timeout: Duration = Duration.ofSeconds(5),
    )
}
