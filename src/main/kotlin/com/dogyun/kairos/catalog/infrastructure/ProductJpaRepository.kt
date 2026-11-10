package com.dogyun.kairos.catalog.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductJpaRepository : JpaRepository<ProductEntity, Long> {
    fun findByIdAndSellerId(id: Long, sellerId: Long): ProductEntity?
    fun existsByCategoryId(categoryId: Long): Boolean

    @Modifying
    @Query("update ProductEntity p set p.salesCount = p.salesCount + :delta where p.id = :productId")
    fun addSalesCount(@Param("productId") productId: Long, @Param("delta") delta: Long): Int

    /** 집계 컬럼은 엔티티에서 `updatable = false` 이므로 이 native 증감만이 바꾼다. */
    @Modifying(flushAutomatically = true)
    @Query("update product set like_count = like_count + :delta where id = :productId", nativeQuery = true)
    fun addLikeCount(@Param("productId") productId: Long, @Param("delta") delta: Long): Int

    @Modifying(flushAutomatically = true)
    @Query(
        "update product set review_count = review_count + :countDelta, rating_sum = rating_sum + :ratingDelta where id = :productId",
        nativeQuery = true,
    )
    fun addReview(@Param("productId") productId: Long, @Param("countDelta") countDelta: Long, @Param("ratingDelta") ratingDelta: Long): Int
}
