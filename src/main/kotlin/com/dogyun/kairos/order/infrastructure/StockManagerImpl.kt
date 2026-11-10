package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.catalog.application.SkuStockService
import com.dogyun.kairos.order.application.ProductQuantity
import com.dogyun.kairos.order.application.SkuQuantity
import com.dogyun.kairos.order.application.StockManager
import org.springframework.stereotype.Component
import com.dogyun.kairos.catalog.application.ProductQuantity as CatalogProductQuantity
import com.dogyun.kairos.catalog.application.SkuQuantity as CatalogSkuQuantity

/** order 의 [StockManager] 를 catalog 의 [SkuStockService] 로 구현한다. */
@Component
class StockManagerImpl(private val skuStockService: SkuStockService) : StockManager {

    override fun decrease(items: List<SkuQuantity>) = skuStockService.decrease(items.map { CatalogSkuQuantity(it.skuId, it.quantity) })

    override fun restore(items: List<SkuQuantity>) = skuStockService.restore(items.map { CatalogSkuQuantity(it.skuId, it.quantity) })

    override fun increaseSales(items: List<ProductQuantity>) =
        skuStockService.increaseSales(items.map { CatalogProductQuantity(it.productId, it.quantity) })

    override fun decreaseSales(items: List<ProductQuantity>) =
        skuStockService.decreaseSales(items.map { CatalogProductQuantity(it.productId, it.quantity) })
}
