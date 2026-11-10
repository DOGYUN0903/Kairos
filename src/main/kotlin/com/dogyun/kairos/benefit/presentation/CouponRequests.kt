package com.dogyun.kairos.benefit.presentation

import com.dogyun.kairos.benefit.application.CreateCouponPolicyCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

/** 값 규칙(할인 종류별 범위, 대상 조합 등)은 도메인이 검증한다. 여기서는 필수 값만 확인한다. */
data class CreateCouponPolicyRequest(
    @field:NotBlank val name: String?,
    @field:NotBlank val couponType: String?,
    @field:NotBlank val targetType: String?,
    val targetProductId: Long?,
    @field:NotBlank val discountType: String?,
    @field:NotNull val discountValue: Long?,
    val maxDiscount: Long?,
    val minOrderAmount: Long?,
    val totalQuantity: Int?,
    val validityDays: Int?,
    @field:NotNull val validFrom: LocalDateTime?,
    @field:NotNull val validUntil: LocalDateTime?,
) {
    fun toCommand() = CreateCouponPolicyCommand(
        name = requireNotNull(name),
        couponType = requireNotNull(couponType),
        targetType = requireNotNull(targetType),
        targetProductId = targetProductId,
        discountType = requireNotNull(discountType),
        discountValue = requireNotNull(discountValue),
        maxDiscount = maxDiscount,
        minOrderAmount = minOrderAmount,
        totalQuantity = totalQuantity,
        validityDays = validityDays,
        validFrom = requireNotNull(validFrom),
        validUntil = requireNotNull(validUntil),
    )
}

data class CreateCouponPolicyResponse(val policyId: Long)

data class ClaimCouponResponse(val couponId: Long, val expiresAt: LocalDateTime)
