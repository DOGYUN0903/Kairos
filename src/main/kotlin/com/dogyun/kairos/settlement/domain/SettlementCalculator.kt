package com.dogyun.kairos.settlement.domain

import java.time.LocalDateTime

data class SettlementSourceItem(
    val orderItemId: Long,
    /** 항목 상태가 ORDERED(취소·반품되지 않음)인지. */
    val ordered: Boolean,
    val lineAmount: Long,
    val productCouponDiscount: Long,
    /** 상품 쿠폰 발급 주체 이름("SELLER" 등). 쿠폰 없으면 null. */
    val productCouponIssuer: String?,
    /** 이 항목 반품 중 판매자 귀책 반품 배송비(완료된 반품만). */
    val sellerBorneReturnFee: Long,
)

data class SettlementSource(
    val sellerOrderId: Long,
    val sellerId: Long,
    val orderId: Long,
    val orderNo: String,
    val confirmedAt: LocalDateTime,
    val commissionRateBp: Int,
    val shippingFee: Long,
    /** 판매자 귀책 반품으로 구매자에게 돌려준 발송 배송비. */
    val buyerShippingFeeRefunded: Long,
    val items: List<SettlementSourceItem>,
)

data class LineCalculation(
    val salesAmount: Long,
    val sellerCouponDiscount: Long,
    val commissionBase: Long,
    val commissionRateBp: Int,
    val commissionAmount: Long,
    val shippingFeeOwed: Long,
    val returnFeeBorne: Long,
    val lineAmount: Long,
)

/** 정산 라인 금액 계산. 순수 함수, 원 단위 정수, 나눗셈은 수수료 한 곳(라인별 버림). */
object SettlementCalculator {
    private const val SELLER_ISSUER = "SELLER"

    fun line(source: SettlementSource): LineCalculation {
        val ordered = source.items.filter { it.ordered }
        val sales = ordered.sumOf { it.lineAmount }
        val sellerCoupon = ordered.filter { it.productCouponIssuer == SELLER_ISSUER }.sumOf { it.productCouponDiscount }
        val base = sales - sellerCoupon
        val commission = commission(base, source.commissionRateBp)
        val shippingOwed = source.shippingFee - source.buyerShippingFeeRefunded
        val returnBorne = source.items.sumOf { it.sellerBorneReturnFee }
        return LineCalculation(
            salesAmount = sales,
            sellerCouponDiscount = sellerCoupon,
            commissionBase = base,
            commissionRateBp = source.commissionRateBp,
            commissionAmount = commission,
            shippingFeeOwed = shippingOwed,
            returnFeeBorne = returnBorne,
            lineAmount = base - commission + shippingOwed - returnBorne,
        )
    }

    fun commission(base: Long, rateBp: Int): Long {
        check(base >= 0) { "수수료 기준 금액은 0 이상이어야 한다: $base" }
        check(rateBp in 0..10_000) { "수수료율(bp)은 0..10000이어야 한다: $rateBp" }
        return Math.floorDiv(base * rateBp, 10_000L)
    }
}
