package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.catalog.domain.OptionValue
import com.dogyun.kairos.catalog.domain.Product
import com.dogyun.kairos.catalog.domain.ProductContent
import com.dogyun.kairos.catalog.domain.ProductId
import com.dogyun.kairos.catalog.domain.ProductOption
import com.dogyun.kairos.catalog.domain.ProductRepository
import com.dogyun.kairos.catalog.domain.ProductStatus
import com.dogyun.kairos.catalog.domain.SellerProductFilter
import com.dogyun.kairos.catalog.domain.Sku
import com.dogyun.kairos.catalog.domain.SkuId
import com.dogyun.kairos.common.domain.ListOrder
import com.dogyun.kairos.common.domain.PageResult
import com.querydsl.core.types.Predicate
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

/**
 * 상품 애그리거트 저장소. 저장 순서는 product → image → option → option_value 로 값 ID 를 먼저 확정하고,
 * 조합(값 데카르트 곱)의 ID 목록으로 [Product.rebuildSkus] 를 호출한 뒤 SKU 를 반영한다.
 * SKU 는 소프트 딜리트하지 않는다(사라진 조합은 DISCONTINUED). 저장 후에는 다시 읽어 ID 가 채워진 도메인을 돌려준다.
 */
@Repository
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
    private val imageJpaRepository: ProductImageJpaRepository,
    private val optionJpaRepository: ProductOptionJpaRepository,
    private val valueJpaRepository: ProductOptionValueJpaRepository,
    private val skuJpaRepository: SkuJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : ProductRepository {

    override fun save(product: Product): Product {
        val entity = saveProduct(product)
        saveImages(entity, product.content.imageFileIds)
        val optionsWithIds = saveOptions(entity, product.content.options)
        product.rebuildSkus(combinations(optionsWithIds))
        saveSkus(entity, product.skus)
        return requireNotNull(findById(ProductId(entity.id))) { "방금 저장한 상품을 다시 읽지 못했습니다: ${entity.id}" }
    }

    override fun findById(id: ProductId): Product? = productJpaRepository.findByIdOrNull(id.value)?.let { toDomains(listOf(it)).single() }

    override fun findByIdAndSellerId(id: ProductId, sellerId: Long): Product? =
        productJpaRepository.findByIdAndSellerId(id.value, sellerId)?.let { toDomains(listOf(it)).single() }

    override fun findPageBySellerId(sellerId: Long, filter: SellerProductFilter, page: Int, size: Int): PageResult<Product> {
        val product = QProductEntity.productEntity
        val where = listOfNotNull(
            product.sellerId.eq(sellerId),
            filter.status?.let { product.productStatus.eq(it) },
            filter.brandId?.let { product.brandId.eq(it) },
            filter.keyword?.let { product.name.like("%${escapeLike(it)}%", LIKE_ESCAPE) },
        )
        return findPage(where, ListOrder.LATEST, page, size)
    }

    /** LIKE 와일드카드(%, _)와 이스케이프 문자를 글자 그대로 찾게 한다. */
    private fun escapeLike(s: String): String =
        s.replace("$LIKE_ESCAPE", "$LIKE_ESCAPE$LIKE_ESCAPE").replace("%", "$LIKE_ESCAPE%").replace("_", "${LIKE_ESCAPE}_")

    override fun findPageByStatus(status: ProductStatus?, order: ListOrder, page: Int, size: Int): PageResult<Product> {
        val product = QProductEntity.productEntity
        return findPage(listOfNotNull(status?.let { product.productStatus.eq(it) }), order, page, size)
    }

    override fun findAllBySkuIds(skuIds: Collection<Long>): List<Product> {
        if (skuIds.isEmpty()) return emptyList()
        val productIds = skuJpaRepository.findAllByIdIn(skuIds).map { it.product.id }.distinct()
        return toDomains(productJpaRepository.findAllById(productIds))
    }

    override fun findAllByIds(ids: Collection<Long>): List<Product> =
        if (ids.isEmpty()) emptyList() else toDomains(productJpaRepository.findAllById(ids.toSet()))

    override fun existsByCategoryId(categoryId: Long): Boolean = productJpaRepository.existsByCategoryId(categoryId)

    private fun findPage(where: List<Predicate>, order: ListOrder, page: Int, size: Int): PageResult<Product> {
        val product = QProductEntity.productEntity
        val content = queryFactory.selectFrom(product)
            .where(*where.toTypedArray())
            .orderBy(if (order == ListOrder.OLDEST) product.id.asc() else product.id.desc())
            .offset(page.toLong() * size)
            .limit(size.toLong())
            .fetch()
        val total = queryFactory.select(product.count()).from(product).where(*where.toTypedArray()).fetchOne() ?: 0L
        return PageResult.of(toDomains(content), page, size, total)
    }

    private fun saveProduct(product: Product): ProductEntity {
        val id = product.id
        val content = product.content
        val entity = if (id == null) {
            ProductEntity(
                product.sellerId, product.brandId, content.categoryId, content.name, content.description,
                product.originalPrice, product.salePrice, product.status, product.rejectReason, product.onSaleAt, product.salesCount,
            )
        } else {
            requireNotNull(productJpaRepository.findByIdOrNull(id.value)) { "존재하지 않는 상품입니다: ${id.value}" }.also {
                it.change(
                    content.categoryId, content.name, content.description, product.originalPrice, product.salePrice,
                    product.status, product.rejectReason, product.onSaleAt,
                )
            }
        }
        return productJpaRepository.saveAndFlush(entity)
    }

    /** 이미지는 순서가 의미를 가지므로 기존 행을 소프트 딜리트하고 다시 만든다. 첫 번째가 대표 이미지. */
    private fun saveImages(entity: ProductEntity, imageFileIds: List<Long>) {
        val current = imageJpaRepository.findAllByProductIdInOrderByDisplayOrderAscIdAsc(listOf(entity.id))
        if (current.map { it.fileId } == imageFileIds) return
        current.forEach { it.softDelete() }
        imageJpaRepository.saveAll(current)
        imageJpaRepository.saveAll(
            imageFileIds.mapIndexed { index, fileId -> ProductImageEntity(entity, fileId, index, isMain = index == 0) },
        )
    }

    /** id 가 있는 옵션·값은 이름과 순서를 갱신, 없는 것은 insert, 콘텐츠에 없는 기존 것은 소프트 딜리트. ID 가 채워진 옵션을 돌려준다. */
    private fun saveOptions(entity: ProductEntity, options: List<ProductOption>): List<ProductOption> {
        val currentOptions = optionJpaRepository.findAllByProductIdInOrderBySortOrderAscIdAsc(listOf(entity.id)).associateBy { it.id }
        val currentValues = if (currentOptions.isEmpty()) emptyList()
        else valueJpaRepository.findAllByOptionIdInOrderBySortOrderAscIdAsc(currentOptions.keys)
        val currentValuesByOption = currentValues.groupBy { it.option.id }

        val keptOptionIds = options.mapNotNull { it.id }.toSet()
        currentOptions.values.filter { it.id !in keptOptionIds }.forEach { it.softDelete() }
        currentValues.filter { it.option.id !in keptOptionIds }.forEach { it.softDelete() }

        val saved = options.mapIndexed { optionIndex, option ->
            val optionEntity = option.id?.let { requireNotNull(currentOptions[it]) { "이 상품의 옵션이 아닙니다: $it" } }
                ?.also { it.change(option.name, optionIndex) }
                ?: optionJpaRepository.save(ProductOptionEntity(entity, option.name, optionIndex))
            val existingValues = currentValuesByOption[optionEntity.id].orEmpty().associateBy { it.id }
            val keptValueIds = option.values.mapNotNull { it.id }.toSet()
            existingValues.values.filter { it.id !in keptValueIds }.forEach { it.softDelete() }
            val values = option.values.mapIndexed { valueIndex, value ->
                val valueEntity = value.id?.let { requireNotNull(existingValues[it]) { "이 옵션의 값이 아닙니다: $it" } }
                    ?.also { it.change(value.name, valueIndex) }
                    ?: valueJpaRepository.save(ProductOptionValueEntity(optionEntity, value.name, valueIndex))
                OptionValue(valueEntity.id, valueEntity.name)
            }
            ProductOption(optionEntity.id, optionEntity.name, values)
        }
        optionJpaRepository.saveAll(currentOptions.values)
        valueJpaRepository.saveAll(currentValues)
        valueJpaRepository.flush()
        return saved
    }

    /** 옵션 순서대로 값을 하나씩 고른 데카르트 곱. 옵션이 없으면 빈 조합 하나. */
    private fun combinations(options: List<ProductOption>): List<List<Long>> =
        options.fold(listOf(emptyList<Long>())) { acc, option ->
            acc.flatMap { prefix -> option.values.map { prefix + requireNotNull(it.id) } }
        }

    private fun saveSkus(entity: ProductEntity, skus: List<Sku>) {
        val current = skuJpaRepository.findAllByProductIdInOrderByIdAsc(listOf(entity.id)).associateBy { it.id }
        val entities = skus.map { sku ->
            val id = sku.id
            if (id == null) {
                SkuEntity(entity, sku.combinationKey, sku.extraPrice, sku.stock, sku.onSale, sku.skuStatus)
            } else {
                requireNotNull(current[id.value]) { "이 상품의 SKU 가 아닙니다: ${id.value}" }
                    .also { it.change(sku.extraPrice, sku.stock, sku.onSale, sku.skuStatus) }
            }
        }
        skuJpaRepository.saveAll(entities)
        skuJpaRepository.flush()
    }

    private fun toDomains(entities: List<ProductEntity>): List<Product> {
        if (entities.isEmpty()) return emptyList()
        val productIds = entities.map { it.id }
        val images = imageJpaRepository.findAllByProductIdInOrderByDisplayOrderAscIdAsc(productIds).groupBy { it.product.id }
        val options = optionJpaRepository.findAllByProductIdInOrderBySortOrderAscIdAsc(productIds)
        val values = if (options.isEmpty()) emptyMap()
        else valueJpaRepository.findAllByOptionIdInOrderBySortOrderAscIdAsc(options.map { it.id }).groupBy { it.option.id }
        val optionsByProduct = options.groupBy { it.product.id }
        val skus = skuJpaRepository.findAllByProductIdInOrderByIdAsc(productIds).groupBy { it.product.id }
        return entities.map { entity ->
            toDomain(
                entity,
                images[entity.id].orEmpty(),
                optionsByProduct[entity.id].orEmpty().map { option ->
                    ProductOption(option.id, option.name, values[option.id].orEmpty().map { OptionValue(it.id, it.name) })
                },
                skus[entity.id].orEmpty(),
            )
        }
    }

    private fun toDomain(
        entity: ProductEntity,
        images: List<ProductImageEntity>,
        options: List<ProductOption>,
        skus: List<SkuEntity>,
    ) = Product.restore(
        id = ProductId(entity.id),
        sellerId = entity.sellerId,
        brandId = entity.brandId,
        content = ProductContent(entity.name, entity.description, entity.categoryId, images.map { it.fileId }, options),
        originalPrice = entity.originalPrice,
        salePrice = entity.salePrice,
        status = entity.productStatus,
        rejectReason = entity.rejectReason,
        onSaleAt = entity.onSaleAt,
        salesCount = entity.salesCount,
        likeCount = entity.likeCount,
        reviewCount = entity.reviewCount,
        ratingSum = entity.ratingSum,
        createdAt = entity.createdAt,
        skus = skus.map {
            Sku(SkuId(it.id), parseValueIds(it.optionValueIds), it.extraPrice, it.stock, it.onSale, it.skuStatus)
        },
    )

    private fun parseValueIds(joined: String): List<Long> = if (joined.isEmpty()) emptyList() else joined.split("|").map(String::toLong)

    private companion object {
        const val LIKE_ESCAPE = '!'
    }
}
