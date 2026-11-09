package com.dogyun.kairos.cart.infrastructure

import com.dogyun.kairos.cart.domain.Cart
import com.dogyun.kairos.cart.domain.CartItem
import com.dogyun.kairos.cart.domain.CartRepository
import org.springframework.stereotype.Repository

@Repository
class CartRepositoryImpl(private val jpaRepository: CartItemJpaRepository) : CartRepository {

    override fun findByMemberId(memberId: Long): Cart =
        Cart(memberId, jpaRepository.findAllByMemberIdOrderById(memberId).map { CartItem(it.id, it.skuId, it.quantity) })

    override fun save(cart: Cart): Cart {
        val persisted = jpaRepository.findAllByMemberIdOrderById(cart.memberId).associateBy { it.id }
        cart.removedItemIds.forEach { persisted[it]?.softDelete() }
        cart.items.forEach { item ->
            val id = item.id
            if (id == null) {
                jpaRepository.save(CartItemEntity(cart.memberId, item.skuId, item.quantity))
            } else {
                persisted[id]?.changeQuantity(item.quantity)
            }
        }
        jpaRepository.flush()
        return findByMemberId(cart.memberId)
    }
}
