package com.dogyun.kairos.catalog.domain

interface CategoryRepository {
    fun save(category: Category): Category
    fun findById(id: CategoryId): Category?

    /** displayOrder, id 오름차순. */
    fun findAll(): List<Category>
    fun findChildren(parentId: CategoryId): List<Category>

    /** 소프트 딜리트. */
    fun delete(id: CategoryId)
}
