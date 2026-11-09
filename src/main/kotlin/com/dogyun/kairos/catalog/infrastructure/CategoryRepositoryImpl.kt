package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.catalog.domain.Category
import com.dogyun.kairos.catalog.domain.CategoryId
import com.dogyun.kairos.catalog.domain.CategoryRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class CategoryRepositoryImpl(private val jpaRepository: CategoryJpaRepository) : CategoryRepository {

    override fun save(category: Category): Category {
        val id = category.id
        val entity = if (id == null) {
            CategoryEntity(category.parentId?.value, category.name, category.displayOrder)
        } else {
            requireNotNull(jpaRepository.findByIdOrNull(id.value)) { "존재하지 않는 카테고리입니다: ${id.value}" }
                .also { it.change(category.name, category.displayOrder) }
        }
        return toDomain(jpaRepository.saveAndFlush(entity))
    }

    override fun findById(id: CategoryId): Category? = jpaRepository.findByIdOrNull(id.value)?.let(::toDomain)

    override fun findAll(): List<Category> = jpaRepository.findAllByOrderByDisplayOrderAscIdAsc().map(::toDomain)

    override fun findChildren(parentId: CategoryId): List<Category> =
        jpaRepository.findAllByParentIdOrderByDisplayOrderAscIdAsc(parentId.value).map(::toDomain)

    override fun delete(id: CategoryId) {
        val entity = jpaRepository.findByIdOrNull(id.value) ?: return
        entity.softDelete()
        jpaRepository.saveAndFlush(entity)
    }

    private fun toDomain(entity: CategoryEntity) = Category.restore(
        CategoryId(entity.id), entity.parentId?.let(::CategoryId), entity.name, entity.displayOrder,
    )
}
