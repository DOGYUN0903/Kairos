package com.dogyun.kairos.deal.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode

class Stock(
    val dealId: DealId,
    val total: Int,
    remaining: Int,
) {
    var remaining: Int = remaining
        private set

    init {
        check(total >= 0 && remaining in 0..total) { "재고 값이 올바르지 않습니다: total=$total, remaining=$remaining" }
    }

    fun decrease(qty: Int) {
        require(qty >= 1) { "차감 수량은 1 이상이어야 합니다: $qty" }
        if (qty > remaining) throw DomainException(ErrorCode.STOCK_SOLD_OUT)
        remaining -= qty
    }

    /** 총수량을 넘는 복구는 불변식 위반(프로그래밍 오류)이므로 IllegalStateException. */
    fun restore(qty: Int) {
        require(qty >= 1) { "복구 수량은 1 이상이어야 합니다: $qty" }
        check(remaining + qty <= total) { "복구 후 재고가 총수량을 초과합니다: remaining=$remaining, qty=$qty, total=$total" }
        remaining += qty
    }
}
