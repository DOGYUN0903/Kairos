package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.OptionValue
import com.dogyun.kairos.catalog.domain.Product
import com.dogyun.kairos.catalog.domain.ProductId
import com.dogyun.kairos.catalog.domain.ProductOption
import com.dogyun.kairos.catalog.domain.ProductRepository
import com.dogyun.kairos.catalog.domain.ProductStatus
import com.dogyun.kairos.catalog.domain.Sku
import com.dogyun.kairos.catalog.domain.SkuId
import com.dogyun.kairos.common.domain.ListOrder
import com.dogyun.kairos.common.domain.PageResult

/** 실제 저장소처럼 새 옵션 값·SKU 에 순번 ID 를 주고, 값 ID 확정 후 [Product.rebuildSkus] 를 호출한다. */
internal class FakeProductRepository : ProductRepository {
    private val store = linkedMapOf<Long, Product>()
    private var productSequence = 0L
    private var optionSequence = 0L
    private var valueSequence = 0L
    private var skuSequence = 0L

    override fun save(product: Product): Product {
        val id = product.id ?: ProductId(++productSequence)
        val options = product.content.options.map { option ->
            ProductOption(
                option.id ?: ++optionSequence,
                option.name,
                option.values.map { OptionValue(it.id ?: ++valueSequence, it.name) },
            )
        }
        val content = product.content.copy(options = options)
        product.rebuildSkus(combinations(options))
        val skus = product.skus.map {
            Sku(it.id ?: SkuId(++skuSequence), it.optionValueIds, it.extraPrice, it.stock, it.onSale, it.skuStatus)
        }
        val saved = Product.restore(
            id, product.sellerId, product.brandId, content, product.originalPrice, product.salePrice,
            product.status, product.rejectReason, product.onSaleAt, product.salesCount, skus,
            createdAt = java.time.LocalDateTime.of(2026, 11, 9, 9, 0),
        )
        store[id.value] = saved
        return saved
    }

    private fun combinations(options: List<ProductOption>): List<List<Long>> =
        options.fold(listOf(emptyList<Long>())) { acc, option ->
            acc.flatMap { prefix -> option.values.map { prefix + requireNotNull(it.id) } }
        }

    override fun findById(id: ProductId): Product? = store[id.value]
    override fun findByIdAndSellerId(id: ProductId, sellerId: Long): Product? =
        store[id.value]?.takeIf { it.sellerId == sellerId }

    override fun findPageBySellerId(sellerId: Long, status: ProductStatus?, page: Int, size: Int): PageResult<Product> {
        val all = store.values.filter { it.sellerId == sellerId && (status == null || it.status == status) }.reversed()
        return PageResult.of(all.drop(page * size).take(size), page, size, all.size.toLong())
    }

    override fun findPageByStatus(status: ProductStatus?, order: ListOrder, page: Int, size: Int): PageResult<Product> {
        val all = store.values.filter { status == null || it.status == status }.let { if (order == ListOrder.OLDEST) it else it.reversed() }
        return PageResult.of(all.drop(page * size).take(size), page, size, all.size.toLong())
    }

    override fun findAllBySkuIds(skuIds: Collection<Long>): List<Product> =
        store.values.filter { product -> product.skus.any { it.id?.value in skuIds } }

    override fun existsByCategoryId(categoryId: Long): Boolean = store.values.any { it.content.categoryId == categoryId }
}
