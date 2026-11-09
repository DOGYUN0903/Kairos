package com.dogyun.kairos.order.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode

data class ShippingAddress(
    val receiverName: String,
    val phone: String,
    val zipCode: String,
    val address1: String,
    val address2: String,
    val memo: String?,
) {
    init {
        if (listOf(receiverName, phone, zipCode, address1, address2).any { it.isBlank() }) {
            throw DomainException(ErrorCode.ORDER_INVALID)
        }
    }
}
