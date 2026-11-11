package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.catalog.domain.Product
import com.dogyun.kairos.catalog.domain.ProductId
import com.dogyun.kairos.catalog.domain.ProductListRow
import com.dogyun.kairos.catalog.domain.ProductRepository
import com.dogyun.kairos.catalog.domain.ProductSearchCondition
import com.dogyun.kairos.catalog.domain.ProductSearchRepository
import com.dogyun.kairos.catalog.domain.ProductSort
import com.dogyun.kairos.catalog.domain.ProductStatus
import com.dogyun.kairos.catalog.domain.SkuStatus
import com.dogyun.kairos.common.domain.PageResult
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.Expression
import com.querydsl.core.types.dsl.CaseBuilder
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

/**
 * 사용자 노출 상품 조회(ADR 0002). `product` 를 [BrandView]·[SellerView] 와 조인해
 * 노출 조건(ON_SALE + 브랜드 APPROVED + 판매자 APPROVED)을 쿼리에서 건다. 소프트 딜리트 조건은 각 엔티티의 `@SQLRestriction` 이 건다.
 * 인덱스는 추가하지 않는다(3-2 측정 대상).
 */
@Repository
class ProductSearchRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
    private val productRepository: ProductRepository,
) : ProductSearchRepository {

    private val product = QProductEntity.productEntity
    private val brand = QBrandView.brandView
    private val seller = QSellerView.sellerView

    override fun search(condition: ProductSearchCondition, page: Int, size: Int): PageResult<ProductListRow> {
        val where = exposed() + filters(condition)
        val mainFileId = mainFileId()
        val soldOut = soldOut()
        val rows = exposedQuery()
            .select(product.id, mainFileId, brand.id, brand.name, product.name, product.originalPrice, product.salePrice, soldOut,
                product.likeCount, product.reviewCount, product.ratingSum)
            .where(*where.toTypedArray())
            .orderBy(*orderBy(condition.sort))
            .offset(page.toLong() * size)
            .limit(size.toLong())
            .fetch()
            .map { tuple ->
                ProductListRow(
                    productId = tuple.get(product.id)!!,
                    mainFileId = tuple.get(mainFileId) ?: 0L,
                    brandId = tuple.get(brand.id)!!,
                    brandName = tuple.get(brand.name)!!,
                    name = tuple.get(product.name)!!,
                    originalPrice = tuple.get(product.originalPrice)!!,
                    salePrice = tuple.get(product.salePrice)!!,
                    soldOut = tuple.get(soldOut) ?: true,
                    likeCount = tuple.get(product.likeCount)!!,
                    reviewCount = tuple.get(product.reviewCount)!!,
                    ratingSum = tuple.get(product.ratingSum)!!,
                )
            }
        val total = exposedQuery().select(product.count()).where(*where.toTypedArray()).fetchOne() ?: 0L
        return PageResult.of(rows, page, size, total)
    }

    override fun findExposedDetail(productId: ProductId): Product? {
        val id = exposedQuery().select(product.id)
            .where(*exposed().toTypedArray(), product.id.eq(productId.value))
            .fetchOne() ?: return null
        return productRepository.findById(ProductId(id))
    }

    private fun exposedQuery(): JPAQuery<*> = queryFactory.from(product)
        .join(brand).on(brand.id.eq(product.brandId))
        .join(seller).on(seller.id.eq(product.sellerId))

    private fun exposed(): List<Predicate> = listOf(
        product.productStatus.eq(ProductStatus.ON_SALE),
        brand.brandStatus.eq(APPROVED),
        seller.sellerStatus.eq(APPROVED),
    )

    private fun filters(condition: ProductSearchCondition): List<Predicate> = listOfNotNull(
        condition.leafCategoryIds?.let { product.categoryId.`in`(it) },
        condition.brandId?.let { product.brandId.eq(it) },
        condition.minPrice?.let { product.salePrice.goe(it) },
        condition.maxPrice?.let { product.salePrice.loe(it) },
        condition.keyword?.let { keywordMatches(it) },
    )

    /** 상품명 또는 브랜드명 부분 일치. `%`, `_`, 이스케이프 문자 자체는 문자로 취급한다. */
    private fun keywordMatches(keyword: String): BooleanExpression {
        val pattern = "%" + escapeLike(keyword) + "%"
        return product.name.like(pattern, LIKE_ESCAPE).or(brand.name.like(pattern, LIKE_ESCAPE))
    }

    private fun escapeLike(raw: String): String = buildString {
        for (ch in raw) {
            if (ch == LIKE_ESCAPE || ch == '%' || ch == '_') append(LIKE_ESCAPE)
            append(ch)
        }
    }

    /** 대표 이미지(is_main) 파일 ID. */
    private fun mainFileId() = JPAExpressions.select(image.fileId).from(image)
        .where(image.product.id.eq(product.id), image.isMain.isTrue)

    /** ACTIVE 이고 판매 중이며 재고가 있는 SKU 가 하나도 없으면 품절. */
    private fun soldOut(): Expression<Boolean> = CaseBuilder()
        .`when`(
            JPAExpressions.selectOne().from(sku)
                .where(sku.product.id.eq(product.id), sku.skuStatus.eq(SkuStatus.ACTIVE), sku.onSale.isTrue, sku.stock.gt(0))
                .exists(),
        ).then(false)
        .otherwise(true)

    private fun orderBy(sort: ProductSort): Array<OrderSpecifier<*>> = when (sort) {
        ProductSort.LATEST -> arrayOf(product.onSaleAt.desc(), product.id.desc())
        ProductSort.PRICE_ASC -> arrayOf(product.salePrice.asc(), product.id.desc())
        ProductSort.PRICE_DESC -> arrayOf(product.salePrice.desc(), product.id.desc())
        ProductSort.LIKES -> arrayOf(product.likeCount.desc(), product.id.desc())
        ProductSort.POPULAR -> arrayOf(product.salesCount.desc(), product.onSaleAt.desc(), product.id.desc())
    }

    private companion object {
        const val APPROVED = "APPROVED"
        const val LIKE_ESCAPE = '\\'
        val image = QProductImageEntity.productImageEntity
        val sku = QSkuEntity.skuEntity
    }
}
