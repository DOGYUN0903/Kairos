package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.catalog.domain.SkuStockRepository
import org.springframework.stereotype.Repository

@Repository
class SkuStockRepositoryImpl(
    private val skuJpaRepository: SkuJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
) : SkuStockRepository {

    override fun decreaseIfEnough(skuId: Long, quantity: Int): Boolean = skuJpaRepository.decreaseIfEnough(skuId, quantity) == 1

    override fun increase(skuId: Long, quantity: Int) {
        skuJpaRepository.increase(skuId, quantity)
    }

    override fun addSalesCount(productId: Long, delta: Long) {
        productJpaRepository.addSalesCount(productId, delta)
    }
}
