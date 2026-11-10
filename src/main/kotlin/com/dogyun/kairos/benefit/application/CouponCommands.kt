package com.dogyun.kairos.benefit.application

import java.time.LocalDateTime

/** 열거값은 문자열로 받아 서비스가 해석한다(알 수 없는 값은 `COUPON_POLICY_INVALID`). */
data class CreateCouponPolicyCommand(
    val name: String,
    val couponType: String,
    val targetType: String,
    val targetProductId: Long?,
    val discountType: String,
    val discountValue: Long,
    val maxDiscount: Long?,
    val minOrderAmount: Long?,
    val totalQuantity: Int?,
    val validityDays: Int?,
    val validFrom: LocalDateTime,
    val validUntil: LocalDateTime,
)
