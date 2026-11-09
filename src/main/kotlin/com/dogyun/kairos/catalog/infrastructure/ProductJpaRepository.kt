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
}
