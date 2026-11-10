package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.cart.application.CartService
import com.dogyun.kairos.order.application.CartItemRemover
import org.springframework.stereotype.Component

/** order 의 [CartItemRemover] 를 cart 의 [CartService] 로 구현한다. */
@Component
class CartItemRemoverImpl(private val cartService: CartService) : CartItemRemover {

    override fun removeBySkuIds(memberId: Long, skuIds: Collection<Long>) = cartService.removeBySkuIds(memberId, skuIds)
}
