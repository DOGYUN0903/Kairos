package com.dogyun.kairos.cart.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode

/** 회원 한 명의 장바구니. 저장소는 [items] 의 새 항목·수량 변경과 [removedItemIds] 의 삭제를 반영한다. */
class Cart(val memberId: Long, items: List<CartItem>) {

    private val _items: MutableList<CartItem> = items.toMutableList()
    private val _removedItemIds: MutableList<Long> = mutableListOf()

    val items: List<CartItem> get() = _items.toList()
    val removedItemIds: List<Long> get() = _removedItemIds.toList()

    /** 같은 SKU 가 이미 있으면 수량을 합산한다. */
    fun add(skuId: Long, quantity: Int): CartItem {
        val existing = _items.firstOrNull { it.skuId == skuId }
        if (existing != null) {
            existing.addQuantity(quantity)
            return existing
        }
        return CartItem(null, skuId, quantity).also { _items.add(it) }
    }

    fun changeQuantity(cartItemId: Long, quantity: Int) {
        find(cartItemId).changeQuantity(quantity)
    }

    fun remove(cartItemId: Long) {
        removeItem(find(cartItemId))
    }

    fun removeBySkuIds(skuIds: Collection<Long>) {
        _items.filter { it.skuId in skuIds }.forEach(::removeItem)
    }

    private fun find(cartItemId: Long): CartItem =
        _items.firstOrNull { it.id == cartItemId } ?: throw DomainException(ErrorCode.CART_ITEM_NOT_FOUND)

    private fun removeItem(item: CartItem) {
        _items.remove(item)
        item.id?.let { _removedItemIds.add(it) }
    }
}
