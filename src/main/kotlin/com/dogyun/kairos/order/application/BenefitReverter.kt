package com.dogyun.kairos.order.application

/**
 * 주문이 결제 전에 닫히거나(만료·승인 거절·void) 항목이 취소될 때 쿠폰·포인트를 되돌리는 창구(ADR 0004, 스펙 7장).
 * 구현은 order.infrastructure 가 benefit.application 을 호출한다. 호출 트랜잭션(주문 행 락 아래)에 참여하고, 재고 복구보다 먼저 부른다
 * (락 순서: orders → member_point → coupon → sku). 포인트 원장 UNIQUE 가 멱등 키이며 예외는 삼키지 않는다.
 */
interface BenefitReverter {
    /** 결제 전에 닫힌 주문: 쿠폰 전부를 만료 전이면 되돌리고, 사용 포인트 전부를 되돌린다(원장 REFUND/ORDER/orderId). */
    fun revertAll(orderId: Long, memberId: Long, couponIds: Collection<Long>, pointsUsed: Long)

    /**
     * 항목 취소 확정: 그 항목의 상품 쿠폰과(마지막 항목이면) 장바구니 쿠폰을 만료 전이면 되돌리고,
     * 배분 포인트를 되돌린다(원장 REFUND/ORDER_ITEM/orderItemId).
     */
    fun revertItem(orderId: Long, memberId: Long, orderItemId: Long, pointsRefund: Long, productCouponId: Long?, cartCouponId: Long?)
}
