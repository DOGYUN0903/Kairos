package com.dogyun.kairos.catalog.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CategoryTest {

    @Test
    fun 소분류_아래에_자식을_만들면_CATEGORY_INVALID() {
        val root = Category.restore(CategoryId(1), null, "패션", 1)
        val leaf = Category.restore(CategoryId(2), CategoryId(1), "신발", 1)
        assertTrue(Category.child(root, "운동화", 1).isLeaf)

        val e = assertFailsWith<DomainException> { Category.child(leaf, "러닝화", 1) }

        assertEquals(ErrorCode.CATEGORY_INVALID, e.errorCode)
    }

    @Test
    fun 대분류는_isLeaf가_false다() {
        assertFalse(Category.root("패션", 1).isLeaf)
    }

    @Test
    fun 이름이_비었거나_50자를_넘으면_CATEGORY_INVALID() {
        assertFailsWith<DomainException> { Category.root(" ", 1) }
        assertFailsWith<DomainException> { Category.root("가".repeat(51), 1) }
        val e = assertFailsWith<DomainException> { Category.root("패션", 1).rename("", 2) }
        assertEquals(ErrorCode.CATEGORY_INVALID, e.errorCode)
    }
}
