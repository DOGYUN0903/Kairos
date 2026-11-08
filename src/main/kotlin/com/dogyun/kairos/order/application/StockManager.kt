package com.dogyun.kairos.order.application

/** deal 모듈 재고 호출용 인터페이스. decrease 는 재고 부족 시 DomainException(STOCK_SOLD_OUT). */
interface StockManager {
    fun decrease(dealId: Long, quantity: Int)
    fun restore(dealId: Long, quantity: Int)
}
