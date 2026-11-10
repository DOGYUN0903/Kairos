package com.dogyun.kairos.benefit.domain

import java.time.LocalDateTime

/** 회원에게 발급된 쿠폰. 사용·복구는 저장소의 조건부 UPDATE 로만 바뀐다. */
class Coupon(
    val id: Long?,
    val policyId: Long,
    val memberId: Long,
    val status: CouponStatus,
    val usedOrderId: Long?,
    val issuedAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val usedAt: LocalDateTime?,
) {
    fun isExpired(now: LocalDateTime): Boolean = !expiresAt.isAfter(now)

    fun displayStatus(now: LocalDateTime): String = when {
        status == CouponStatus.USED -> "USED"
        isExpired(now) -> "EXPIRED"
        else -> "AVAILABLE"
    }
}
