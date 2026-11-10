package com.dogyun.kairos.catalog.domain

/** 주문 쪽 재고·판매량 변경 포트. 조건부 UPDATE 한 문장으로 처리한다(락 없음). */
interface SkuStockRepository {
    /** 재고가 quantity 이상일 때만 차감하고 true. 부족하면 false(변경 없음). */
    fun decreaseIfEnough(skuId: Long, quantity: Int): Boolean

    /** DISCONTINUED SKU 여도 되돌린다. */
    fun increase(skuId: Long, quantity: Int)

    /** 판매량에 delta 를 더한다(음수 가능). */
    fun addSalesCount(productId: Long, delta: Long)
}
