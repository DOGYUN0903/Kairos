package com.dogyun.kairos.payment.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("kairos.pg")
data class PgProperties(
    val provider: String = "mock",
    val toss: Toss = Toss(),
) {
    data class Toss(
        val baseUrl: String = "https://api.tosspayments.com",
        val secretKey: String = "",
        val timeout: Duration = Duration.ofSeconds(10),
    )
}
