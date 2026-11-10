package com.dogyun.kairos.cart.application

import com.dogyun.kairos.cart.domain.CartRepository
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CartService(
    private val cartRepository: CartRepository,
    private val productReader: ProductReader,
) {

    /** 담은 항목의 ID. 같은 SKU 를 다시 담으면 기존 항목 ID 가 돌아온다. */
    @Transactional
    fun add(memberId: Long, skuId: Long, quantity: Int): Long {
        val sku = productReader.findOrderableSkus(listOf(skuId))[skuId] ?: throw DomainException(ErrorCode.SKU_NOT_FOUND)
        if (sku.availability != AVAILABLE) throw DomainException(ErrorCode.SKU_NOT_ORDERABLE)
        val cart = cartRepository.findByMemberId(memberId)
        cart.add(skuId, quantity)
        return requireNotNull(cartRepository.save(cart).items.first { it.skuId == skuId }.id)
    }

    @Transactional
    fun changeQuantity(memberId: Long, cartItemId: Long, quantity: Int) {
        val cart = cartRepository.findByMemberId(memberId)
        cart.changeQuantity(cartItemId, quantity)
        cartRepository.save(cart)
    }

    @Transactional
    fun remove(memberId: Long, cartItemId: Long) {
        val cart = cartRepository.findByMemberId(memberId)
        cart.remove(cartItemId)
        cartRepository.save(cart)
    }

    /** 주문 완료 후 주문한 SKU 를 비운다(order 가 호출). 없는 SKU 는 무시한다. */
    @Transactional
    fun removeBySkuIds(memberId: Long, skuIds: Collection<Long>) {
        val cart = cartRepository.findByMemberId(memberId)
        cart.removeBySkuIds(skuIds)
        cartRepository.save(cart)
    }

    private companion object {
        const val AVAILABLE = "AVAILABLE"
    }
}
