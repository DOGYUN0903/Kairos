package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.catalog.domain.LikedProductRow
import com.dogyun.kairos.catalog.domain.ProductLikeRepository
import com.dogyun.kairos.common.domain.PageResult
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.Clock
import java.time.LocalDateTime

@Repository
class ProductLikeRepositoryImpl(
    private val jpaRepository: ProductLikeJpaRepository,
    private val queryFactory: JPAQueryFactory,
    private val clock: Clock,
) : ProductLikeRepository {

    private val like = QProductLikeEntity.productLikeEntity
    private val product = QProductEntity.productEntity
    private val brand = QBrandView.brandView
    private val image = QProductImageEntity.productImageEntity

    /** 행 확보(X 락) → 취소였으면 좋아요로. 영향 행 1 이 "내가 상태를 바꿨다"이므로 동시 요청이 몰려도 true 는 전이마다 정확히 한 번이다. */
    override fun like(productId: Long, memberId: Long): Boolean {
        val now = LocalDateTime.now(clock)
        jpaRepository.ensureRow(productId, memberId, now)
        return jpaRepository.reactivate(productId, memberId, now) == 1
    }

    override fun unlike(productId: Long, memberId: Long): Boolean =
        jpaRepository.unlike(productId, memberId, LocalDateTime.now(clock)) == 1

    override fun findLikedProductIds(memberId: Long, productIds: Collection<Long>): Set<Long> {
        if (productIds.isEmpty()) return emptySet()
        return jpaRepository.findAllByMemberIdAndProductIdInAndLikedTrue(memberId, productIds).map { it.productId }.toSet()
    }

    /** 노출 조건을 걸지 않는다: 판매 종료·중지 상품도 상태와 함께 남는다. 소프트 딜리트된 상품·브랜드만 조인에서 빠진다. */
    override fun findPageLiked(memberId: Long, page: Int, size: Int): PageResult<LikedProductRow> {
        val where = arrayOf(like.memberId.eq(memberId), like.liked.isTrue)
        val mainFileId = JPAExpressions.select(image.fileId).from(image).where(image.product.id.eq(product.id), image.isMain.isTrue)
        val rows = queryFactory.from(like)
            .join(product).on(product.id.eq(like.productId))
            .join(brand).on(brand.id.eq(product.brandId))
            .select(product.id, product.name, brand.name, mainFileId, product.salePrice, product.productStatus, like.modifiedAt)
            .where(*where)
            .orderBy(like.modifiedAt.desc(), like.id.desc())
            .offset(page.toLong() * size)
            .limit(size.toLong())
            .fetch()
            .map {
                LikedProductRow(
                    productId = it.get(product.id)!!,
                    name = it.get(product.name)!!,
                    brandName = it.get(brand.name)!!,
                    mainFileId = it.get(mainFileId) ?: 0L,
                    salePrice = it.get(product.salePrice)!!,
                    productStatus = it.get(product.productStatus)!!.name,
                    likedAt = it.get(like.modifiedAt)!!,
                )
            }
        val total = queryFactory.select(like.count()).from(like)
            .join(product).on(product.id.eq(like.productId))
            .join(brand).on(brand.id.eq(product.brandId))
            .where(*where).fetchOne() ?: 0L
        return PageResult.of(rows, page, size, total)
    }
}
