package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.CategoryId
import com.dogyun.kairos.catalog.domain.CategoryRepository
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CategoryQueryService(private val categoryRepository: CategoryRepository) {

    /** 대분류 → 소분류 트리. displayOrder, id 오름차순. */
    fun tree(): List<CategoryNode> {
        val all = categoryRepository.findAll()
        val childrenByParent = all.filter { it.parentId != null }.groupBy { it.parentId!!.value }
        return all.filter { it.parentId == null }.map { root ->
            val rootId = requireNotNull(root.id).value
            CategoryNode(
                rootId, root.name, root.displayOrder,
                childrenByParent[rootId].orEmpty().map {
                    CategoryNode(requireNotNull(it.id).value, it.name, it.displayOrder, emptyList())
                },
            )
        }
    }

    /** 상품 필터용. 소분류면 자기 자신, 대분류면 자식 전부. */
    fun leafIdsUnder(categoryId: Long): List<Long> {
        val category = categoryRepository.findById(CategoryId(categoryId))
            ?: throw DomainException(ErrorCode.CATEGORY_NOT_FOUND)
        if (category.isLeaf) return listOf(categoryId)
        return categoryRepository.findChildren(CategoryId(categoryId)).map { requireNotNull(it.id).value }
    }

    /** 상품은 소분류에만 속할 수 있다. */
    fun requireLeaf(categoryId: Long) {
        val category = categoryRepository.findById(CategoryId(categoryId))
            ?: throw DomainException(ErrorCode.CATEGORY_NOT_FOUND)
        if (!category.isLeaf) throw DomainException(ErrorCode.CATEGORY_NOT_LEAF)
    }
}
