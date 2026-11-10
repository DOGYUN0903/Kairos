package com.dogyun.kairos.order.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode

/**
 * 포인트 사용 정책 값. 설정은 benefit 모듈(`kairos.benefit.*`)에만 있고, order 는 포트를 통해 이 값을 받아 쓴다.
 * 최소 사용 가능 잔액, 사용 단위, 쿠폰 적용 후 금액 대비 최대 사용 비율(bp).
 */
data class PointUsePolicy(val minUsableBalance: Long, val useUnit: Long, val maxUseRateBp: Int)

data class LineAllocation(
    val skuId: Long,
    val productCouponId: Long?,
    val productCouponDiscount: Long,
    val productCouponIssuer: CouponIssuer?,
    val cartCouponAllocated: Long,
    val pointsAllocated: Long,
)

data class DiscountAllocation(
    val lines: Map<Long, LineAllocation>,
    val productCouponDiscount: Long,
    val cartCouponId: Long?,
    val cartCouponDiscount: Long,
    val pointsUsed: Long,
) {
    companion object {
        /** 할인이 없는 배분. */
        fun none(lines: List<OrderLine>) = DiscountAllocation(
            lines = lines.associate { it.skuId to LineAllocation(it.skuId, null, 0, null, 0, 0) },
            productCouponDiscount = 0, cartCouponId = null, cartCouponDiscount = 0, pointsUsed = 0,
        )
    }
}

/** 스펙 4-2, 5-3. 상품 쿠폰 -> 장바구니 쿠폰 -> 포인트 순서로 항목별 배분액을 계산하는 순수 함수. 할인 계산은 이 한 곳에서만 한다. */
object DiscountAllocator {

    fun allocate(
        lines: List<OrderLine>,
        productCoupons: Map<Long, CouponTerms>,
        cartCoupon: CouponTerms?,
        pointsToUse: Long,
        pointBalance: Long,
        policy: PointUsePolicy,
    ): DiscountAllocation {
        val sorted = lines.sortedWith(compareBy({ it.sellerId }, { it.skuId }))
        val amounts = sorted.map { (it.salePrice + it.extraPrice) * it.quantity }

        // 1. 상품 쿠폰
        if (productCoupons.keys.any { skuId -> sorted.none { it.skuId == skuId } }) {
            throw DomainException(ErrorCode.COUPON_NOT_APPLICABLE)
        }
        val productDiscounts = sorted.mapIndexed { i, line ->
            val terms = productCoupons[line.skuId] ?: return@mapIndexed 0L
            if (terms.kind != CouponKind.PRODUCT || !terms.appliesTo(line.productId, line.sellerId)) {
                throw DomainException(ErrorCode.COUPON_NOT_APPLICABLE)
            }
            if ((terms.minOrderAmount ?: 0) > amounts[i]) throw DomainException(ErrorCode.COUPON_MIN_AMOUNT_NOT_MET)
            terms.discountFor(amounts[i])
        }
        val afterProduct = amounts.indices.map { amounts[it] - productDiscounts[it] }

        // 2. 장바구니 쿠폰
        var cartDiscount = 0L
        var cartShares = List(sorted.size) { 0L }
        if (cartCoupon != null) {
            if (cartCoupon.kind != CouponKind.CART) throw DomainException(ErrorCode.COUPON_NOT_APPLICABLE)
            val target = afterProduct.sum()
            if (target <= 0) throw DomainException(ErrorCode.COUPON_NOT_APPLICABLE)
            if ((cartCoupon.minOrderAmount ?: 0) > target) throw DomainException(ErrorCode.COUPON_MIN_AMOUNT_NOT_MET)
            cartDiscount = cartCoupon.discountFor(target)
            cartShares = distribute(cartDiscount, afterProduct)
        }
        val afterCoupon = afterProduct.indices.map { afterProduct[it] - cartShares[it] }
        val afterCouponTotal = afterCoupon.sum()

        // 3. 포인트
        if (pointsToUse < 0 || pointsToUse % policy.useUnit != 0L) throw DomainException(ErrorCode.POINT_INVALID_AMOUNT)
        var pointShares = List(sorted.size) { 0L }
        if (pointsToUse > 0) {
            if (afterCouponTotal <= 0 || pointBalance < policy.minUsableBalance) throw DomainException(ErrorCode.POINT_EXCEEDS_LIMIT)
            if (pointsToUse > pointBalance) throw DomainException(ErrorCode.POINT_INSUFFICIENT)
            if (pointsToUse > maxUsablePoints(afterCouponTotal, pointBalance, policy)) throw DomainException(ErrorCode.POINT_EXCEEDS_LIMIT)
            pointShares = distribute(pointsToUse, afterCoupon)
        }

        val allocations = sorted.mapIndexed { i, line ->
            val terms = productCoupons[line.skuId]
            check(amounts[i] - productDiscounts[i] - cartShares[i] - pointShares[i] >= 0) { "항목 할인 합이 금액을 넘는다: ${line.skuId}" }
            line.skuId to LineAllocation(
                line.skuId, terms?.couponId, productDiscounts[i], terms?.issuer, cartShares[i], pointShares[i],
            )
        }.toMap()
        return DiscountAllocation(allocations, productDiscounts.sum(), cartCoupon?.couponId, cartDiscount, pointsToUse)
    }

    /** 쓸 수 있는 포인트 최대치. 잔액이 최소 사용 가능 잔액 미만이거나 쿠폰 후 금액이 0 이하면 0. */
    fun maxUsablePoints(afterCouponAmount: Long, pointBalance: Long, policy: PointUsePolicy): Long {
        if (pointBalance < policy.minUsableBalance || afterCouponAmount <= 0) return 0
        val cap = minOf(pointBalance, afterCouponAmount * policy.maxUseRateBp / 10_000)
        return cap / policy.useUnit * policy.useUnit
    }

    /**
     * [total] 을 [weights] 비율로 나눈다. 각자 버림하고 나머지는 마지막 항목에 얹는다.
     * 마지막 항목의 가중치(잔액)보다 나머지가 크면 앞쪽 항목으로 넘긴다(드문 경계).
     */
    private fun distribute(total: Long, weights: List<Long>): List<Long> {
        if (total == 0L) return weights.map { 0L }
        val sum = weights.sum()
        val shares = weights.map { total * it / sum }.toMutableList()
        var rest = total - shares.sum()
        for (i in shares.indices.reversed()) {
            if (rest == 0L) break
            val add = minOf(rest, weights[i] - shares[i])
            shares[i] += add
            rest -= add
        }
        check(rest == 0L) { "배분 나머지를 소진하지 못했다" }
        return shares
    }
}
