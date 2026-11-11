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

    /** 같은 부모 아래 카테고리 전부를 주어진 순서대로 0.. 으로 다시 매긴다. 목록이 정확히 일치하지 않으면 아무것도 바꾸지 않는다. */
    @Transactional
    fun reorder(parentId: Long?, orderedIds: List<Long>) {
        val siblings = if (parentId == null) {
            categoryRepository.findAll().filter { it.parentId == null }
        } else {
            val parent = find(parentId)
            categoryRepository.findChildren(requireNotNull(parent.id))
        }
        val byId = siblings.associateBy { requireNotNull(it.id).value }
        if (orderedIds.size != orderedIds.toSet().size || orderedIds.toSet() != byId.keys) {
            throw DomainException(ErrorCode.CATEGORY_INVALID)
        }
        orderedIds.forEachIndexed { index, id ->
            val category = requireNotNull(byId[id])
            if (category.displayOrder != index) {
                category.rename(category.name, index)
                categoryRepository.save(category)
            }
        }
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
