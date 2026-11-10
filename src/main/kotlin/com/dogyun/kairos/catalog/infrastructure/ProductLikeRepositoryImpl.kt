package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.catalog.domain.LikedProductRow
import com.dogyun.kairos.catalog.domain.ProductLikeRepository
import com.dogyun.kairos.common.domain.PageResult
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Clock
import java.time.LocalDateTime

@Repository
class ProductLikeRepositoryImpl(
    private val jpaRepository: ProductLikeJpaRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
) : ProductLikeRepository {

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

    /**
     * 노출 조건을 걸지 않고, 소프트 딜리트된 상품·브랜드도 남긴다(스펙 9장). `@SQLRestriction` 이 붙은 엔티티를 거치지 않으려고
     * native SQL 로 읽는다. 삭제된 상품은 `productStatus = DELETED` 로 표시한다.
     */
    override fun findPageLiked(memberId: Long, page: Int, size: Int): PageResult<LikedProductRow> {
        val rows = jdbcTemplate.query(
            """
            SELECT p.id, p.name, COALESCE(b.name, ''),
                   COALESCE((SELECT pi.file_id FROM product_image pi WHERE pi.product_id = p.id AND pi.is_main = 1 AND pi.status = 'ACTIVE' LIMIT 1), 0),
                   p.sale_price, CASE WHEN p.status = 'DELETED' THEN 'DELETED' ELSE p.product_status END, l.modified_at
            FROM product_like l
            JOIN product p ON p.id = l.product_id
            LEFT JOIN brand b ON b.id = p.brand_id
            WHERE l.member_id = ? AND l.liked = 1 AND l.status = 'ACTIVE'
            ORDER BY l.modified_at DESC, l.id DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            { rs, _ ->
                LikedProductRow(
                    productId = rs.getLong(1),
                    name = rs.getString(2),
                    brandName = rs.getString(3),
                    mainFileId = rs.getLong(4),
                    salePrice = rs.getLong(5),
                    productStatus = rs.getString(6),
                    likedAt = rs.getObject(7, LocalDateTime::class.java),
                )
            },
            memberId, size, page.toLong() * size,
        )
        val total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_like l JOIN product p ON p.id = l.product_id WHERE l.member_id = ? AND l.liked = 1 AND l.status = 'ACTIVE'",
            Long::class.java, memberId,
        ) ?: 0L
        return PageResult.of(rows, page, size, total)
    }
}
