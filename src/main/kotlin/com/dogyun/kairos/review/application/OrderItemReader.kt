package com.dogyun.kairos.review.application

import java.time.LocalDateTime

/** order 모듈의 주문 항목 조회 포트. 구현은 review.infrastructure 가 order application 서비스로 한다. */
interface OrderItemReader {
    /** 내 주문 항목이 아니거나 없으면 null. */
    fun findConfirmedItem(memberId: Long, orderItemId: Long): ConfirmedOrderItem?
}

/** [confirmedAt] 은 판매자 주문이 `CONFIRMED` 이고 항목이 `ORDERED` 일 때만 채워진다. */
data class ConfirmedOrderItem(
    val orderId: Long,
    val orderItemId: Long,
    val productId: Long,
    val sellerId: Long,
    val productName: String,
    val confirmedAt: LocalDateTime?,
)
