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
        // Phase 1은 oversell을 허용하므로 복구 후 remaining이 total을 넘을 수 있다(상한 검사 없음).
        check(total >= 0 && remaining >= 0) { "재고 값이 올바르지 않습니다: total=$total, remaining=$remaining" }
    }

    fun decrease(qty: Int) {
        require(qty >= 1) { "차감 수량은 1 이상이어야 합니다: $qty" }
        if (qty > remaining) throw DomainException(ErrorCode.STOCK_SOLD_OUT)
        remaining -= qty
    }

    /** Phase 1: 락이 없어 oversell이 가능하므로 복구는 total로 cap하지 않는다(Phase 3에서 재검토). */
    fun restore(qty: Int) {
        require(qty >= 1) { "복구 수량은 1 이상이어야 합니다: $qty" }
        remaining += qty
    }
}
