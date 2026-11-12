package com.dogyun.kairos.order.application

import com.dogyun.kairos.order.domain.PointUsePolicy
import java.time.LocalDateTime

/** benefit 이 넘겨 준 쿠폰 조건의 복사본. 문자열 값은 benefit enum 이름(order 는 benefit 을 import 하지 않는다). */
data class ReservedCoupon(
    val couponId: Long,
    val policyId: Long,
    /** PRODUCT, CART */
    val couponType: String,
    /** PLATFORM, SELLER */
    val issuerType: String,
    /** ALL_PRODUCTS, SELLER, PRODUCT, NONE */
    val targetType: String,
    val targetProductId: Long?,
    val sellerId: Long?,
    /** FIXED, RATE(bp) */
    val discountType: String,
    val discountValue: Long,
    val maxDiscount: Long?,
    val minOrderAmount: Long?,
)

/** 내가 쓸 수 있는 쿠폰 한 장: 조건 + 정책 이름 + 쿠폰 만료일. */
data class UsableCoupon(val terms: ReservedCoupon, val name: String, val expiresAt: LocalDateTime)

/** 쿠폰 조건(쿠폰 ID → 조건), 포인트 잔액, 포인트 사용 정책(설정은 benefit 에만 있다). */
data class BenefitReservation(
    val coupons: Map<Long, ReservedCoupon>,
    val pointBalance: Long,
    val pointPolicy: PointUsePolicy,
)

/**
 * order 가 benefit(쿠폰·포인트)을 주문 생성에 적용하는 창구(ADR 0004). 구현은 order.infrastructure 가 benefit.application 을 호출한다.
 * [reserve]·[commit] 은 주문 생성 트랜잭션 안에서 불려야 한다.
 */
interface BenefitApplier {
    /** 미리보기: 락도 UPDATE 도 없다. 남의 것·없는 쿠폰은 `COUPON_NOT_FOUND`, 사용됨·만료는 `COUPON_NOT_AVAILABLE`. */
    fun quote(memberId: Long, couponIds: Collection<Long>): BenefitReservation

    /** 견적용: 회원의 `AVAILABLE`·만료 전 쿠폰 전부. 락도 UPDATE 도 없다. */
    fun listUsable(memberId: Long): List<UsableCoupon>

    /** `member_point` 행을 잠근 뒤 쿠폰을 `AVAILABLE → USED` 로 바꾼다(실패 시 `COUPON_NOT_AVAILABLE`, 남의 것·없음 `COUPON_NOT_FOUND`). 잔액은 락 뒤에 읽은 값이다. */
    fun reserve(memberId: Long, couponIds: Collection<Long>): BenefitReservation

    /** 주문 저장 뒤: 쿠폰에 주문 ID 를 기록하고 포인트를 FIFO 로 차감한다(원장 `USE`). */
    fun commit(memberId: Long, orderId: Long, couponIds: Collection<Long>, pointsToUse: Long)
}
