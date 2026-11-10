package com.dogyun.kairos.cart.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CartServiceTest {

    private val repository = FakeCartRepository()
    private val reader = FakeProductReader()
    private val service = CartService(repository, reader)
    private val queryService = CartQueryService(repository, reader)

    private fun assertCode(code: ErrorCode, block: () -> Unit) {
        assertEquals(code, assertFailsWith<DomainException> { block() }.errorCode)
    }

    @Test
    fun AVAILABLE_SKU만_담을_수_있다_SOLD_OUT은_SKU_NOT_ORDERABLE() {
        reader.put(1L)
        reader.put(2L, availability = "SOLD_OUT")
        service.add(7L, 1L, 2)
        assertCode(ErrorCode.SKU_NOT_ORDERABLE) { service.add(7L, 2L, 1) }
    }

    @Test
    fun 없는_SKU는_SKU_NOT_FOUND() {
        assertCode(ErrorCode.SKU_NOT_FOUND) { service.add(7L, 99L, 1) }
    }

    @Test
    fun 같은_SKU를_다시_담으면_같은_항목_ID가_돌아오고_수량이_합쳐진다() {
        reader.put(1L)
        val first = service.add(7L, 1L, 2)
        val second = service.add(7L, 1L, 3)
        assertEquals(first, second)
        assertEquals(5, queryService.get(7L).items.single().quantity)
    }

    @Test
    fun 조회는_현재_가격과_가용_상태를_계산하고_AVAILABLE_합계만_availableAmount다() {
        reader.put(1L, unitPrice = 9000)
        reader.put(2L, unitPrice = 5000)
        reader.put(3L, unitPrice = 1000)
        service.add(7L, 1L, 2)
        service.add(7L, 2L, 1)
        service.add(7L, 3L, 1)
        reader.put(1L, unitPrice = 10000)
        reader.put(2L, unitPrice = 5000, availability = "SOLD_OUT")
        reader.skus.remove(3L)

        val result = queryService.get(7L)

        val byAvail = result.items.associateBy { it.skuId }
        assertEquals(20000L, byAvail.getValue(1L).lineAmount)
        assertEquals("SOLD_OUT", byAvail.getValue(2L).availability)
        assertEquals(5000L, byAvail.getValue(2L).lineAmount)
        assertEquals("DISCONTINUED", byAvail.getValue(3L).availability)
        assertEquals(0L, byAvail.getValue(3L).unitPrice)
        assertEquals(0L, byAvail.getValue(3L).lineAmount)
        assertEquals(20000L, result.availableAmount)
    }

    @Test
    fun 다른_회원의_항목은_CART_ITEM_NOT_FOUND() {
        reader.put(1L)
        val itemId = service.add(7L, 1L, 1)
        assertCode(ErrorCode.CART_ITEM_NOT_FOUND) { service.changeQuantity(8L, itemId, 2) }
        assertCode(ErrorCode.CART_ITEM_NOT_FOUND) { service.remove(8L, itemId) }
    }

    @Test
    fun 수량_변경과_삭제와_SKU별_삭제() {
        reader.put(1L)
        reader.put(2L)
        val a = service.add(7L, 1L, 1)
        service.add(7L, 2L, 1)
        service.changeQuantity(7L, a, 4)
        assertEquals(4, queryService.get(7L).items.first { it.skuId == 1L }.quantity)
        service.remove(7L, a)
        assertEquals(listOf(2L), queryService.get(7L).items.map { it.skuId })
        service.removeBySkuIds(7L, listOf(2L))
        assertEquals(0, queryService.get(7L).items.size)
    }
}
