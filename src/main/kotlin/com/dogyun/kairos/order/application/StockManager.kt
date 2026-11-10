package com.dogyun.kairos.order.application

data class SkuQuantity(val skuId: Long, val quantity: Int)

data class ProductQuantity(val productId: Long, val quantity: Int)

/** order 가 catalog 의 재고·판매량을 바꾸는 창구. 구현은 order.infrastructure 에서 catalog 의 SkuStockService 로 한다. */
interface StockManager {
    /** 하나라도 부족하면 STOCK_SOLD_OUT 으로 던지고 호출 트랜잭션이 전부 롤백한다. */
    fun decrease(items: List<SkuQuantity>)
    fun restore(items: List<SkuQuantity>)
    fun increaseSales(items: List<ProductQuantity>)
    fun decreaseSales(items: List<ProductQuantity>)
}
