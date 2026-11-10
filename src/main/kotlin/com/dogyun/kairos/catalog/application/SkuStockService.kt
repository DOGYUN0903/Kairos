package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.SkuStockRepository
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SkuStockService(private val skuStockRepository: SkuStockRepository) {

    /** SKU ID 오름차순으로 차감한다(교착 방지). 하나라도 부족하면 예외를 던져 호출 트랜잭션이 전부 롤백한다. */
    @Transactional
    fun decrease(items: List<SkuQuantity>) {
        items.sortedBy { it.skuId }.forEach {
            if (!skuStockRepository.decreaseIfEnough(it.skuId, it.quantity)) throw DomainException(ErrorCode.STOCK_SOLD_OUT)
        }
    }

    @Transactional
    fun restore(items: List<SkuQuantity>) {
        items.sortedBy { it.skuId }.forEach { skuStockRepository.increase(it.skuId, it.quantity) }
    }

    @Transactional
    fun increaseSales(items: List<ProductQuantity>) {
        items.sortedBy { it.productId }.forEach { skuStockRepository.addSalesCount(it.productId, it.quantity.toLong()) }
    }

    @Transactional
    fun decreaseSales(items: List<ProductQuantity>) {
        items.sortedBy { it.productId }.forEach { skuStockRepository.addSalesCount(it.productId, -it.quantity.toLong()) }
    }
}
