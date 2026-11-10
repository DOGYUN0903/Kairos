package com.dogyun.kairos.cart.domain

interface CartRepository {
    fun findByMemberId(memberId: Long): Cart

    /** 새 항목 INSERT, 수량 UPDATE, [Cart.removedItemIds] 소프트 딜리트. 저장 후 다시 읽은 장바구니를 돌려준다. */
    fun save(cart: Cart): Cart
}
