package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.Category
import com.dogyun.kairos.catalog.domain.CategoryId
import com.dogyun.kairos.catalog.domain.CategoryRepository
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CategoryServiceTest {

    private class FakeCategoryRepository : CategoryRepository {
        private val store = linkedMapOf<Long, Category>()
        private var sequence = 0L

        override fun save(category: Category): Category {
            val id = category.id ?: CategoryId(++sequence)
            val saved = Category.restore(id, category.parentId, category.name, category.displayOrder)
            store[id.value] = saved
            return saved
        }

        override fun findById(id: CategoryId): Category? = store[id.value]
        override fun findAll(): List<Category> = sorted(store.values)
        override fun findChildren(parentId: CategoryId): List<Category> = sorted(store.values.filter { it.parentId == parentId })
        override fun delete(id: CategoryId) { store.remove(id.value) }

        private fun sorted(list: Collection<Category>) = list.sortedWith(compareBy({ it.displayOrder }, { it.id!!.value }))
    }

    private class FakeUsageChecker(var used: Boolean = false) : CategoryUsageChecker {
        override fun hasProducts(categoryId: Long) = used
    }

    private val repository = FakeCategoryRepository()
    private val usageChecker = FakeUsageChecker()
    private val service = CategoryService(repository, usageChecker)
    private val queryService = CategoryQueryService(repository)

    @Test
    fun 트리는_displayOrder_순으로_자식을_품는다() {
        val b = service.create(CreateCategoryCommand(null, "뷰티", 2))
        val a = service.create(CreateCategoryCommand(null, "패션", 1))
        service.create(CreateCategoryCommand(a, "신발", 2))
        service.create(CreateCategoryCommand(a, "상의", 1))

        val tree = queryService.tree()

        assertEquals(listOf("패션", "뷰티"), tree.map { it.name })
        assertEquals(listOf("상의", "신발"), tree[0].children.map { it.name })
        assertEquals(b, tree[1].categoryId)
        assertEquals(emptyList(), tree[1].children)
    }

    @Test
    fun 없는_부모_아래에_만들면_CATEGORY_NOT_FOUND() {
        val e = assertFailsWith<DomainException> { service.create(CreateCategoryCommand(99, "신발", 1)) }
        assertEquals(ErrorCode.CATEGORY_NOT_FOUND, e.errorCode)
    }

    @Test
    fun 이름과_순서를_수정한다() {
        val id = service.create(CreateCategoryCommand(null, "패션", 1))

        service.update(id, UpdateCategoryCommand("의류", 5))

        assertEquals("의류", queryService.tree().single().name)
        assertEquals(5, queryService.tree().single().displayOrder)
    }

    @Test
    fun 자식이_있는_대분류_삭제는_CATEGORY_HAS_CHILDREN() {
        val root = service.create(CreateCategoryCommand(null, "패션", 1))
        service.create(CreateCategoryCommand(root, "신발", 1))

        val e = assertFailsWith<DomainException> { service.delete(root) }

        assertEquals(ErrorCode.CATEGORY_HAS_CHILDREN, e.errorCode)
    }

    @Test
    fun 상품이_있는_소분류_삭제는_CATEGORY_HAS_PRODUCTS() {
        val root = service.create(CreateCategoryCommand(null, "패션", 1))
        val leaf = service.create(CreateCategoryCommand(root, "신발", 1))
        usageChecker.used = true

        val e = assertFailsWith<DomainException> { service.delete(leaf) }

        assertEquals(ErrorCode.CATEGORY_HAS_PRODUCTS, e.errorCode)
    }

    @Test
    fun 비어_있는_소분류는_삭제된다() {
        val root = service.create(CreateCategoryCommand(null, "패션", 1))
        val leaf = service.create(CreateCategoryCommand(root, "신발", 1))

        service.delete(leaf)

        assertEquals(emptyList(), queryService.tree().single().children)
    }

    @Test
    fun leafIdsUnder는_대분류면_자식_ID_목록이다() {
        val root = service.create(CreateCategoryCommand(null, "패션", 1))
        val shoes = service.create(CreateCategoryCommand(root, "신발", 1))
        val tops = service.create(CreateCategoryCommand(root, "상의", 2))

        assertEquals(listOf(shoes, tops), queryService.leafIdsUnder(root))
        assertEquals(listOf(shoes), queryService.leafIdsUnder(shoes))
        val e = assertFailsWith<DomainException> { queryService.leafIdsUnder(999) }
        assertEquals(ErrorCode.CATEGORY_NOT_FOUND, e.errorCode)
    }

    @Test
    fun requireLeaf는_대분류면_CATEGORY_NOT_LEAF() {
        val root = service.create(CreateCategoryCommand(null, "패션", 1))
        val leaf = service.create(CreateCategoryCommand(root, "신발", 1))

        queryService.requireLeaf(leaf)
        val notLeaf = assertFailsWith<DomainException> { queryService.requireLeaf(root) }
        val missing = assertFailsWith<DomainException> { queryService.requireLeaf(999) }

        assertEquals(ErrorCode.CATEGORY_NOT_LEAF, notLeaf.errorCode)
        assertEquals(ErrorCode.CATEGORY_NOT_FOUND, missing.errorCode)
    }

    @Test
    fun 순서_변경은_목록_순서대로_0부터_다시_매긴다() {
        val a = service.create(CreateCategoryCommand(null, "패션", 5))
        val b = service.create(CreateCategoryCommand(null, "뷰티", 5))

        service.reorder(null, listOf(b, a))

        assertEquals(listOf("뷰티", "패션"), queryService.tree().map { it.name })
        assertEquals(listOf(0, 1), queryService.tree().map { it.displayOrder })
    }

    @Test
    fun 순서_변경_목록에_다른_부모의_ID가_섞이면_CATEGORY_INVALID이고_변경이_없다() {
        val a = service.create(CreateCategoryCommand(null, "패션", 1))
        val b = service.create(CreateCategoryCommand(null, "뷰티", 2))
        val child = service.create(CreateCategoryCommand(a, "상의", 1))

        val e = assertFailsWith<DomainException> { service.reorder(null, listOf(b, a, child)) }

        assertEquals(ErrorCode.CATEGORY_INVALID, e.errorCode)
        assertEquals(listOf(1, 2), queryService.tree().map { it.displayOrder })
    }
}
