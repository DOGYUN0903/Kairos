package com.dogyun.kairos.catalog.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface ProductOptionJpaRepository : JpaRepository<ProductOptionEntity, Long> {
    fun findAllByProductIdInOrderBySortOrderAscIdAsc(productIds: Collection<Long>): List<ProductOptionEntity>
}
