package com.dogyun.kairos.catalog.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface ProductImageJpaRepository : JpaRepository<ProductImageEntity, Long> {
    fun findAllByProductIdInOrderByDisplayOrderAscIdAsc(productIds: Collection<Long>): List<ProductImageEntity>
}
