package com.dogyun.kairos.catalog.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface CategoryJpaRepository : JpaRepository<CategoryEntity, Long> {
    fun findAllByOrderByDisplayOrderAscIdAsc(): List<CategoryEntity>
    fun findAllByParentIdOrderByDisplayOrderAscIdAsc(parentId: Long): List<CategoryEntity>
}
