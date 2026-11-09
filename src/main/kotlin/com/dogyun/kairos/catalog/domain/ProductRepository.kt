package com.dogyun.kairos.catalog.domain

import com.dogyun.kairos.common.domain.PageResult

interface ProductRepository {
    /** 저장 시 새 옵션 값의 ID 를 확정한 뒤 [Product.rebuildSkus] 를 호출해 SKU 를 맞춘다. */
    fun save(product: Product): Product
    fun findById(id: ProductId): Product?
    fun findByIdAndSellerId(id: ProductId, sellerId: Long): Product?
    fun findPageBySellerId(sellerId: Long, status: ProductStatus?, page: Int, size: Int): PageResult<Product>
    fun findPageByStatus(status: ProductStatus?, page: Int, size: Int): PageResult<Product>
    fun existsByCategoryId(categoryId: Long): Boolean
}
