package com.dogyun.kairos.order.domain

enum class CouponKind { PRODUCT, CART }

enum class CouponIssuer { PLATFORM, SELLER }

enum class CouponTarget { ALL_PRODUCTS, SELLER, PRODUCT, NONE }

enum class CouponDiscountType { FIXED, RATE }

/**
 * 주문 시점에 order 가 아는 쿠폰 조건(benefit 모듈이 넘겨 준 값의 복사본). order 는 benefit 을 import 하지 않는다.
 * 금액은 원 단위 정수, 정률은 bp(10000 = 100%)이며 버림이다.
 */
data class CouponTerms(
    val couponId: Long,
    val kind: CouponKind,
    val issuer: CouponIssuer,
    val target: CouponTarget,
    val targetProductId: Long?,
    val targetSellerId: Long?,
    val discountType: CouponDiscountType,
    val discountValue: Long,
    val maxDiscount: Long?,
    val minOrderAmount: Long?,
) {
    fun appliesTo(productId: Long, sellerId: Long): Boolean = when (target) {
        CouponTarget.ALL_PRODUCTS -> true
        CouponTarget.SELLER -> sellerId == targetSellerId
        CouponTarget.PRODUCT -> productId == targetProductId
        CouponTarget.NONE -> false
    }

    /** 최소 주문 금액 충족 여부(경계 포함). */
    fun meetsMinimum(targetAmount: Long): Boolean = (minOrderAmount ?: 0) <= targetAmount

    /** [targetAmount] 에 대한 할인액. 대상 금액을 넘지 않는다. */
    fun discountFor(targetAmount: Long): Long {
        if (targetAmount <= 0) return 0
        return when (discountType) {
            CouponDiscountType.FIXED -> minOf(discountValue, targetAmount)
            CouponDiscountType.RATE -> {
                val raw = targetAmount * discountValue / 10_000
                minOf(raw, maxDiscount ?: Long.MAX_VALUE, targetAmount)
            }
        }
    }
}
