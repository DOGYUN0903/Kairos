package com.dogyun.kairos.order.application

/** `kairos.order.placed` 의 result 태그 값. 주문 생성 요청 하나당 정확히 하나가 기록된다. */
enum class PlaceOutcome { PAID, PAYMENT_FAILED, PAYMENT_TIMEOUT, SOLD_OUT, LIMIT_EXCEEDED, DEAL_NOT_ONGOING, OTHER }

/**
 * 주문 비즈니스 메트릭. 구현(Micrometer)은 order.infrastructure 에 둔다.
 * 순판매량(sold)은 DB 값이 아니라 커밋된 PAID 주문 수량의 메모리 누적이다(설계 문서 5.3).
 */
interface OrderMetrics {
    fun recordPlaced(result: PlaceOutcome)
    fun registerDeal(dealId: Long, totalQuantity: Int)
    fun addSold(dealId: Long, quantity: Int)
    fun subtractSold(dealId: Long, quantity: Int)
}
