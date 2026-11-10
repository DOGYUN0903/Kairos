package com.dogyun.kairos.benefit.application

import com.dogyun.kairos.benefit.domain.CouponPolicy
import java.time.LocalDateTime

data class CouponPolicyResult(
    val policyId: Long,
    val name: String,
    val issuerType: String,
    val couponType: String,
    val targetType: String,
    val targetProductId: Long?,
    val discountType: String,
    val discountValue: Long,
    val maxDiscount: Long?,
    val minOrderAmount: Long?,
    val totalQuantity: Int?,
    val issuedCount: Int,
    val validityDays: Int?,
    val validFrom: LocalDateTime,
    val validUntil: LocalDateTime,
    val status: String,
    val claimed: Boolean,
)

data class CouponResult(
    val couponId: Long,
    val policyId: Long,
    val name: String,
    val couponType: String,
    val discountType: String,
    val discountValue: Long,
    val maxDiscount: Long?,
    val minOrderAmount: Long?,
    val status: String,
    val issuedAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val usedOrderId: Long?,
    val usedAt: LocalDateTime?,
)

data class ClaimedCoupon(val couponId: Long, val expiresAt: LocalDateTime)

internal fun CouponPolicy.toResult(claimed: Boolean) = CouponPolicyResult(
    policyId = requireNotNull(id),
    name = name,
    issuerType = issuerType.name,
    couponType = couponType.name,
    targetType = targetType.name,
    targetProductId = targetProductId,
    discountType = discountType.name,
    discountValue = discountValue,
    maxDiscount = maxDiscount,
    minOrderAmount = minOrderAmount,
    totalQuantity = totalQuantity,
    issuedCount = issuedCount,
    validityDays = validityDays,
    validFrom = validFrom,
    validUntil = validUntil,
    status = status.name,
    claimed = claimed,
)

/** 주문에 쓸 쿠폰의 할인 조건(정책 값). 금액은 원, 정률은 bp. */
data class CouponTermsResult(
    val couponId: Long,
    val policyId: Long,
    val couponType: String,
    val issuerType: String,
    val targetType: String,
    val targetProductId: Long?,
    val sellerId: Long?,
    val discountType: String,
    val discountValue: Long,
    val maxDiscount: Long?,
    val minOrderAmount: Long?,
)
