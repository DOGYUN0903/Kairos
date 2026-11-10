package com.dogyun.kairos.cart.application

import com.dogyun.kairos.cart.domain.Cart
import com.dogyun.kairos.cart.domain.CartItem
import com.dogyun.kairos.cart.domain.CartRepository

class FakeCartRepository : CartRepository {
    private data class Row(val id: Long, val memberId: Long, val skuId: Long, var quantity: Int)

    private val rows = mutableListOf<Row>()
    private var nextId = 1L

    override fun findByMemberId(memberId: Long): Cart =
        Cart(memberId, rows.filter { it.memberId == memberId }.map { CartItem(it.id, it.skuId, it.quantity) })

    override fun save(cart: Cart): Cart {
        rows.removeAll { it.id in cart.removedItemIds }
        cart.items.forEach { item ->
            val id = item.id
            if (id == null) rows.add(Row(nextId++, cart.memberId, item.skuId, item.quantity))
            else rows.first { it.id == id }.quantity = item.quantity
        }
        return findByMemberId(cart.memberId)
    }
}
