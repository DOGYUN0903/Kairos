package com.dogyun.kairos.catalog.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface SkuJpaRepository : JpaRepository<SkuEntity, Long> {
    fun findAllByProductIdInOrderByIdAsc(productIds: Collection<Long>): List<SkuEntity>
}
