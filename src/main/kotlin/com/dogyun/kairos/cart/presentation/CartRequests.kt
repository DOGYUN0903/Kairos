package com.dogyun.kairos.cart.presentation

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class AddCartItemRequest(
    @field:Min(1) val skuId: Long,
    @field:Min(1) @field:Max(999) val quantity: Int,
)

data class AddCartItemResponse(val cartItemId: Long)

data class ChangeCartItemQuantityRequest(
    @field:Min(1) @field:Max(999) val quantity: Int,
)
