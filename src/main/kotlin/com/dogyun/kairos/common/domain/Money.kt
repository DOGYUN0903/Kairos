package com.dogyun.kairos.common.domain

@JvmInline
value class Money(val amount: Long) {
    init {
        require(amount >= 0) { "금액은 음수일 수 없습니다: $amount" }
    }

    operator fun times(qty: Int): Money = Money(amount * qty)
}
