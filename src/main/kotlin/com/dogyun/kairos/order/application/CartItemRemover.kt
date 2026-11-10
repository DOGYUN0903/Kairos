package com.dogyun.kairos.order.application

/** 결제가 끝난 주문의 SKU 를 장바구니에서 비우는 창구. 구현은 order.infrastructure 에서 cart 의 CartService 로 한다. */
interface CartItemRemover {
    fun removeBySkuIds(memberId: Long, skuIds: Collection<Long>)
}
