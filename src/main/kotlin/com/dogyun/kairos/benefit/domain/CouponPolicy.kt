package com.dogyun.kairos.benefit.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.LocalDateTime

/** 쿠폰 정책. 발급 수량 [issuedCount] 는 저장소의 조건부 UPDATE 로만 올라간다(도메인 메서드로 바꾸지 않는다). */
class CouponPolicy(
    val id: Long?,
    val name: String,
    val issuerType: IssuerType,
    val sellerId: Long?,
    val couponType: CouponType,
    val targetType: TargetType,
    val targetProductId: Long?,
    val discountType: DiscountType,
    val discountValue: Long,
    val maxDiscount: Long?,
    val minOrderAmount: Long?,
    val totalQuantity: Int?,
    val issuedCount: Int,
    val validityDays: Int?,
    val validFrom: LocalDateTime,
    val validUntil: LocalDateTime,
    status: CouponPolicyStatus,
) {
    var status: CouponPolicyStatus = status
        private set

    fun end() {
        status = CouponPolicyStatus.ENDED
    }

    /** ACTIVE 이고 `validFrom <= now < validUntil` 일 때만 발급할 수 있다. */
    fun requireClaimable(now: LocalDateTime) {
        if (status != CouponPolicyStatus.ACTIVE || now.isBefore(validFrom) || !now.isBefore(validUntil)) {
            throw DomainException(ErrorCode.COUPON_POLICY_ENDED)
        }
    }

    fun expiresAtFor(issuedAt: LocalDateTime): LocalDateTime =
        minOf(validUntil, validityDays?.let { issuedAt.plusDays(it.toLong()) } ?: validUntil)

    companion object {
        private const val MAX_NAME_LENGTH = 50
        private const val MAX_RATE_BP = 10_000L

        @Suppress("LongParameterList")
        fun create(
            name: String,
            issuerType: IssuerType,
            sellerId: Long?,
            couponType: CouponType,
            targetType: TargetType,
            targetProductId: Long?,
            discountType: DiscountType,
            discountValue: Long,
            maxDiscount: Long?,
            minOrderAmount: Long?,
            totalQuantity: Int?,
            validityDays: Int?,
            validFrom: LocalDateTime,
            validUntil: LocalDateTime,
        ): CouponPolicy {
            require(name.trim().length in 1..MAX_NAME_LENGTH)
            require(
                when (discountType) {
                    DiscountType.FIXED -> discountValue > 0
                    DiscountType.RATE -> discountValue in 1..MAX_RATE_BP && maxDiscount != null && maxDiscount > 0
                },
            )
            require(minOrderAmount == null || minOrderAmount > 0)
            require(totalQuantity == null || totalQuantity > 0)
            require(validityDays == null || validityDays > 0)
            require(validFrom.isBefore(validUntil))
            require(validTarget(issuerType, sellerId, couponType, targetType))
            require((targetType == TargetType.PRODUCT) == (targetProductId != null))
            return CouponPolicy(
                null, name.trim(), issuerType, sellerId, couponType, targetType, targetProductId, discountType,
                discountValue, maxDiscount, minOrderAmount, totalQuantity, 0, validityDays, validFrom, validUntil,
                CouponPolicyStatus.ACTIVE,
            )
        }

        private fun validTarget(issuer: IssuerType, sellerId: Long?, couponType: CouponType, targetType: TargetType): Boolean =
            when {
                issuer == IssuerType.SELLER ->
                    sellerId != null && couponType == CouponType.PRODUCT && targetType in setOf(TargetType.SELLER, TargetType.PRODUCT)
                couponType == CouponType.CART -> targetType == TargetType.NONE
                else -> targetType in setOf(TargetType.ALL_PRODUCTS, TargetType.PRODUCT)
            }

        private fun require(condition: Boolean) {
            if (!condition) throw DomainException(ErrorCode.COUPON_POLICY_INVALID)
        }
    }
}
