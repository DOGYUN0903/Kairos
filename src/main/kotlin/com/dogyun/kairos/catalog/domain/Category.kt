package com.dogyun.kairos.catalog.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode

/** 2단계 카테고리. parentId 가 없으면 대분류, 있으면 소분류(상품은 소분류에만 속한다). */
class Category(
    val id: CategoryId?,
    val parentId: CategoryId?,
    name: String,
    displayOrder: Int,
) {
    var name: String = name
        private set
    var displayOrder: Int = displayOrder
        private set

    val isLeaf: Boolean get() = parentId != null

    fun rename(name: String, displayOrder: Int) {
        validate(name)
        this.name = name
        this.displayOrder = displayOrder
    }

    companion object {
        const val MAX_NAME_LENGTH = 50

        fun root(name: String, displayOrder: Int): Category {
            validate(name)
            return Category(null, null, name, displayOrder)
        }

        /** 소분류 아래에는 더 만들 수 없다(2단계 제한). */
        fun child(parent: Category, name: String, displayOrder: Int): Category {
            if (parent.isLeaf) throw DomainException(ErrorCode.CATEGORY_INVALID)
            validate(name)
            return Category(null, requireNotNull(parent.id) { "저장되지 않은 부모 카테고리입니다." }, name, displayOrder)
        }

        fun restore(id: CategoryId, parentId: CategoryId?, name: String, displayOrder: Int) =
            Category(id, parentId, name, displayOrder)

        private fun validate(name: String) {
            if (name.isBlank() || name.length > MAX_NAME_LENGTH) throw DomainException(ErrorCode.CATEGORY_INVALID)
        }
    }
}
