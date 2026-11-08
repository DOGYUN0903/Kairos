package com.dogyun.kairos.common.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** secret 은 HS256 요구사항에 따라 256bit(32바이트) 이상이어야 한다. */
@ConfigurationProperties("kairos.jwt")
data class JwtProperties(
    val secret: String,
    val accessTtl: Duration = Duration.ofMinutes(30),
    val refreshTtl: Duration = Duration.ofDays(14),
)
