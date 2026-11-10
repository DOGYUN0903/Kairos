package com.dogyun.kairos.cart.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode

class CartItem(val id: Long?, val skuId: Long, quantity: Int) {

    var quantity: Int = validated(quantity)
        private set

    fun changeQuantity(quantity: Int) {
        this.quantity = validated(quantity)
    }

    fun addQuantity(quantity: Int) {
        this.quantity = validated(this.quantity + quantity)
    }

    companion object {
        const val MAX_QUANTITY = 999

        private fun validated(quantity: Int): Int {
            if (quantity !in 1..MAX_QUANTITY) throw DomainException(ErrorCode.CART_INVALID)
            return quantity
        }
    }
}
