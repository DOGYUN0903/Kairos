package com.dogyun.kairos.catalog.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface ProductOptionValueJpaRepository : JpaRepository<ProductOptionValueEntity, Long> {
    fun findAllByOptionIdInOrderBySortOrderAscIdAsc(optionIds: Collection<Long>): List<ProductOptionValueEntity>
}
