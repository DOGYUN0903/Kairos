package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.Category
import com.dogyun.kairos.catalog.domain.CategoryId
import com.dogyun.kairos.catalog.domain.CategoryRepository
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val usageChecker: CategoryUsageChecker,
) {

    @Transactional
    fun create(command: CreateCategoryCommand): Long {
        val category = if (command.parentId == null) {
            Category.root(command.name, command.displayOrder)
        } else {
            val parent = categoryRepository.findById(CategoryId(command.parentId))
                ?: throw DomainException(ErrorCode.CATEGORY_NOT_FOUND)
            Category.child(parent, command.name, command.displayOrder)
        }
        return requireNotNull(categoryRepository.save(category).id).value
    }

    @Transactional
    fun update(categoryId: Long, command: UpdateCategoryCommand) {
        val category = find(categoryId)
        category.rename(command.name, command.displayOrder)
        categoryRepository.save(category)
    }

    @Transactional
    fun delete(categoryId: Long) {
        val category = find(categoryId)
        val id = requireNotNull(category.id)
        if (categoryRepository.findChildren(id).isNotEmpty()) throw DomainException(ErrorCode.CATEGORY_HAS_CHILDREN)
        if (usageChecker.hasProducts(id.value)) throw DomainException(ErrorCode.CATEGORY_HAS_PRODUCTS)
        categoryRepository.delete(id)
    }

    private fun find(categoryId: Long): Category =
        categoryRepository.findById(CategoryId(categoryId)) ?: throw DomainException(ErrorCode.CATEGORY_NOT_FOUND)
}
