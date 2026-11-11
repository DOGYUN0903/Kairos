package com.dogyun.kairos.catalog.domain

import com.dogyun.kairos.common.domain.ListOrder
import com.dogyun.kairos.common.domain.PageResult

interface ProductRepository {
    /** 저장 시 새 옵션 값의 ID 를 확정한 뒤 [Product.rebuildSkus] 를 호출해 SKU 를 맞춘다. */
    fun save(product: Product): Product
    fun findById(id: ProductId): Product?
    fun findByIdAndSellerId(id: ProductId, sellerId: Long): Product?
    fun findPageBySellerId(sellerId: Long, filter: SellerProductFilter, page: Int, size: Int): PageResult<Product>
    /** [order] 는 id 기준(LATEST 내림차순, OLDEST 오름차순). */
    fun findPageByStatus(status: ProductStatus?, order: ListOrder, page: Int, size: Int): PageResult<Product>
    /** SKU 가 속한 상품(DISCONTINUED SKU 포함). 없는 SKU ID 는 무시하고 같은 상품은 한 번만 돌려준다. */
    fun findAllBySkuIds(skuIds: Collection<Long>): List<Product>
    fun existsByCategoryId(categoryId: Long): Boolean
}
