package com.dogyun.kairos.order.domain

enum class OrderItemStatus {
    ORDERED, CANCEL_REQUESTED, CANCELED, RETURN_REQUESTED, RETURN_APPROVED, RETURN_REFUNDING, RETURNED;

    /** 반품이 진행 중인 항목. 이 항목들은 아직 '남은 항목'이다. */
    val isOpenReturn: Boolean get() = this == RETURN_REQUESTED || this == RETURN_APPROVED || this == RETURN_REFUNDING

    val isRemaining: Boolean get() = this == ORDERED || isOpenReturn
}
