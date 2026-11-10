package com.dogyun.kairos.benefit.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** 포인트·후기 보상 정책값. 쿠폰은 정책 행이 값을 가지므로 여기에 두지 않는다. */
@ConfigurationProperties("kairos.benefit")
data class BenefitProperties(
    val earnRateBp: Int = 100,
    val minUsableBalance: Long = 1_000,
    val useUnit: Long = 10,
    val maxUseRateBp: Int = 3000,
    val earnExpiry: Duration = Duration.ofDays(365),
    val refundLotExpiry: Duration = Duration.ofDays(30),
    val reviewRewardText: Long = 100,
    val reviewRewardPhoto: Long = 500,
)
