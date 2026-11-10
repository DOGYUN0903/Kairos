package com.dogyun.kairos.order.application

/**
 * 구매 확정 시 결제 금액의 일부를 포인트로 적립하는 창구(스펙 6장, ADR 0004).
 * 구현은 order.infrastructure 가 benefit.application 을 호출한다. 호출 트랜잭션(주문 행 락 아래)에 참여하며
 * 원장 UNIQUE(`EARN_PURCHASE/PURCHASE_CONFIRM/sellerOrderId`)로 멱등이다. 락 순서: orders → member_point.
 */
interface PointEarner {
    /** 적립한 포인트를 돌려준다. 이미 적립했거나 적립액이 0이면 0. */
    fun earnForPurchase(memberId: Long, sellerOrderId: Long, paidAmount: Long): Long
}
