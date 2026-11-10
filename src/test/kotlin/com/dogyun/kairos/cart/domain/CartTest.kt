package com.dogyun.kairos.cart.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CartTest {

    private fun assertCode(code: ErrorCode, block: () -> Unit) {
        assertEquals(code, assertFailsWith<DomainException> { block() }.errorCode)
    }

    @Test
    fun 같은_SKU를_다시_담으면_수량이_합쳐진다() {
        val cart = Cart(1L, emptyList())
        cart.add(10L, 2)
        cart.add(10L, 3)
        assertEquals(1, cart.items.size)
        assertEquals(5, cart.items.single().quantity)
    }

    @Test
    fun 수량_1000은_CART_INVALID() {
        val cart = Cart(1L, listOf(CartItem(1L, 10L, 999)))
        assertCode(ErrorCode.CART_INVALID) { cart.add(10L, 1) }
        assertCode(ErrorCode.CART_INVALID) { cart.add(11L, 1000) }
        assertCode(ErrorCode.CART_INVALID) { cart.changeQuantity(1L, 1000) }
        assertCode(ErrorCode.CART_INVALID) { cart.changeQuantity(1L, 0) }
    }

    @Test
    fun 없는_항목_삭제는_CART_ITEM_NOT_FOUND() {
        val cart = Cart(1L, listOf(CartItem(1L, 10L, 1)))
        assertCode(ErrorCode.CART_ITEM_NOT_FOUND) { cart.remove(99L) }
        assertCode(ErrorCode.CART_ITEM_NOT_FOUND) { cart.changeQuantity(99L, 1) }
    }

    @Test
    fun 삭제한_항목은_removedItemIds에_기록된다() {
        val cart = Cart(1L, listOf(CartItem(1L, 10L, 1), CartItem(2L, 11L, 1)))
        cart.remove(1L)
        assertEquals(listOf(1L), cart.removedItemIds)
        assertEquals(listOf(11L), cart.items.map { it.skuId })
    }

    @Test
    fun removeBySkuIds는_해당_SKU_항목만_지운다() {
        val cart = Cart(1L, listOf(CartItem(1L, 10L, 1), CartItem(2L, 11L, 1), CartItem(3L, 12L, 1)))
        cart.removeBySkuIds(listOf(10L, 12L, 99L))
        assertEquals(listOf(11L), cart.items.map { it.skuId })
        assertEquals(listOf(1L, 3L), cart.removedItemIds)
    }
}
