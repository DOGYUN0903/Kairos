package com.dogyun.kairos.catalog.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface ProductJpaRepository : JpaRepository<ProductEntity, Long> {
    fun findByIdAndSellerId(id: Long, sellerId: Long): ProductEntity?
    fun existsByCategoryId(categoryId: Long): Boolean
}
