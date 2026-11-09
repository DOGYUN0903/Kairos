package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.Category
import com.dogyun.kairos.catalog.domain.CategoryId
import com.dogyun.kairos.catalog.domain.CategoryRepository
import com.dogyun.kairos.catalog.domain.OptionValue
import com.dogyun.kairos.catalog.domain.Product
import com.dogyun.kairos.catalog.domain.ProductContent
import com.dogyun.kairos.catalog.domain.ProductId
import com.dogyun.kairos.catalog.domain.ProductOption
import com.dogyun.kairos.catalog.domain.ProductRepository
import com.dogyun.kairos.catalog.domain.ProductStatus
import com.dogyun.kairos.catalog.domain.Sku
import com.dogyun.kairos.catalog.domain.SkuId
import com.dogyun.kairos.catalog.domain.SkuStatus
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.common.security.SellerPrincipal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProductServiceTest {

    /** 실제 저장소처럼 새 옵션 값·SKU 에 순번 ID 를 주고, 값 ID 확정 후 [Product.rebuildSkus] 를 호출한다. */
    private class FakeProductRepository : ProductRepository {
        private val store = linkedMapOf<Long, Product>()
        private var productSequence = 0L
        private var optionSequence = 0L
        private var valueSequence = 0L
        private var skuSequence = 0L

        override fun save(product: Product): Product {
            val id = product.id ?: ProductId(++productSequence)
            val options = product.content.options.map { option ->
                ProductOption(
                    option.id ?: ++optionSequence,
                    option.name,
                    option.values.map { OptionValue(it.id ?: ++valueSequence, it.name) },
                )
            }
            val content = product.content.copy(options = options)
            product.rebuildSkus(combinations(options))
            val skus = product.skus.map {
                Sku(it.id ?: SkuId(++skuSequence), it.optionValueIds, it.extraPrice, it.stock, it.onSale, it.skuStatus)
            }
            val saved = Product.restore(
                id, product.sellerId, product.brandId, content, product.originalPrice, product.salePrice,
                product.status, product.rejectReason, product.onSaleAt, product.salesCount, skus,
            )
            store[id.value] = saved
            return saved
        }

        private fun combinations(options: List<ProductOption>): List<List<Long>> =
            options.fold(listOf(emptyList<Long>())) { acc, option ->
                acc.flatMap { prefix -> option.values.map { prefix + requireNotNull(it.id) } }
            }

        override fun findById(id: ProductId): Product? = store[id.value]
        override fun findByIdAndSellerId(id: ProductId, sellerId: Long): Product? =
            store[id.value]?.takeIf { it.sellerId == sellerId }

        override fun findPageBySellerId(sellerId: Long, status: ProductStatus?, page: Int, size: Int): PageResult<Product> {
            val all = store.values.filter { it.sellerId == sellerId && (status == null || it.status == status) }.reversed()
            return PageResult.of(all.drop(page * size).take(size), page, size, all.size.toLong())
        }

        override fun findPageByStatus(status: ProductStatus?, page: Int, size: Int): PageResult<Product> {
            val all = store.values.filter { status == null || it.status == status }.reversed()
            return PageResult.of(all.drop(page * size).take(size), page, size, all.size.toLong())
        }

        override fun existsByCategoryId(categoryId: Long): Boolean = store.values.any { it.content.categoryId == categoryId }
    }

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
        override fun findAll(): List<Category> = store.values.toList()
        override fun findChildren(parentId: CategoryId): List<Category> = store.values.filter { it.parentId == parentId }
        override fun delete(id: CategoryId) { store.remove(id.value) }
    }

    private class FakeSellerReader(private val brands: Map<Long, BrandSnapshot>) : SellerReader {
        override fun findBrand(brandId: Long): BrandSnapshot? = brands[brandId]
        override fun getBrandNames(brandIds: Collection<Long>): Map<Long, String> =
            brands.filterKeys { it in brandIds }.mapValues { it.value.name }
    }

    private class FakeImageFileReader(private val files: Map<Long, ImageFileRef>) : ImageFileReader {
        override fun findImages(fileIds: Collection<Long>): Map<Long, ImageFileRef> = files.filterKeys { it in fileIds }
    }

    private val me = SellerPrincipal(sellerId = 1L, memberId = 1L, canManage = true, canView = true)
    private val other = SellerPrincipal(sellerId = 2L, memberId = 2L, canManage = true, canView = true)

    private val products = FakeProductRepository()
    private val categories = FakeCategoryRepository()
    private val sellers = FakeSellerReader(
        mapOf(
            10L to BrandSnapshot(10L, sellerId = 1L, name = "키로스", approved = true),
            11L to BrandSnapshot(11L, sellerId = 2L, name = "남의브랜드", approved = true),
            12L to BrandSnapshot(12L, sellerId = 1L, name = "심사중", approved = false),
        ),
    )
    private val files = FakeImageFileReader(
        mapOf(
            100L to ImageFileRef(100L, "http://files/a.png", uploaderMemberId = 1L, isImage = true),
            101L to ImageFileRef(101L, "http://files/b.png", uploaderMemberId = 1L, isImage = true),
            200L to ImageFileRef(200L, "http://files/x.png", uploaderMemberId = 2L, isImage = true),
        ),
    )
    private val categoryQueryService = CategoryQueryService(categories)
    private val service = ProductService(products, sellers, categoryQueryService, files)
    private val query = ProductQueryService(products, sellers, files)

    private val root: Long = requireNotNull(categories.save(Category.root("패션", 1)).id).value
    private val leaf: Long = requireNotNull(categories.save(Category.child(categories.findById(CategoryId(root))!!, "상의", 1)).id).value

    private fun colorSize() = listOf(
        OptionCommand(null, "색상", listOf(OptionValueCommand(null, "블랙"), OptionValueCommand(null, "화이트"))),
        OptionCommand(null, "사이즈", listOf(OptionValueCommand(null, "M"), OptionValueCommand(null, "L"))),
    )

    private fun content(
        categoryId: Long = leaf,
        imageFileIds: List<Long> = listOf(100L, 101L),
        options: List<OptionCommand> = colorSize(),
    ) = ProductContentCommand("티셔츠", "설명", categoryId, imageFileIds, options)

    private fun register(brandId: Long = 10L, content: ProductContentCommand = content()) =
        service.register(me, RegisterProductCommand(brandId, content, originalPrice = 10000, salePrice = 9000))

    private fun assertCode(expected: ErrorCode, block: () -> Unit) {
        val e = assertFailsWith<DomainException> { block() }
        assertEquals(expected, e.errorCode)
    }

    @Test
    fun 등록하면_PENDING_REVIEW_상품이_sellerId와_함께_저장되고_SKU_4개가_생긴다() {
        val productId = register()

        val detail = query.getMine(me, productId)

        assertEquals("PENDING_REVIEW", detail.status)
        assertEquals(1L, detail.sellerId)
        assertEquals("키로스", detail.brandName)
        assertEquals(4, detail.skus.size)
        assertTrue(detail.skus.all { it.skuStatus == "ACTIVE" && it.stock == 0 && it.extraPrice == 0L && it.onSale })
        assertEquals(listOf("블랙 / M", "블랙 / L", "화이트 / M", "화이트 / L"), detail.skus.map { it.displayName })
        assertEquals(listOf("http://files/a.png", "http://files/b.png"), detail.images.map { it.url })
        assertEquals(listOf(true, false), detail.images.map { it.isMain })
        assertEquals(10, detail.discountRate)
        assertTrue(detail.soldOut)
        val summary = query.listMine(me, null, 0, 20).content.single()
        assertEquals("http://files/a.png", summary.thumbnailUrl)
        assertEquals("키로스", summary.brandName)
    }

    @Test
    fun 남의_브랜드로_등록하면_BRAND_NOT_FOUND() {
        assertCode(ErrorCode.BRAND_NOT_FOUND) { register(brandId = 11L) }
        assertCode(ErrorCode.BRAND_NOT_FOUND) { register(brandId = 999L) }
    }

    @Test
    fun 미승인_브랜드는_BRAND_NOT_APPROVED() {
        assertCode(ErrorCode.BRAND_NOT_APPROVED) { register(brandId = 12L) }
    }

    @Test
    fun 대분류_카테고리는_CATEGORY_NOT_LEAF() {
        assertCode(ErrorCode.CATEGORY_NOT_LEAF) { register(content = content(categoryId = root)) }
    }

    @Test
    fun 다른_회원이_올린_이미지는_FILE_NOT_OWNED() {
        assertCode(ErrorCode.FILE_NOT_OWNED) { register(content = content(imageFileIds = listOf(100L, 200L))) }
        assertCode(ErrorCode.FILE_NOT_OWNED) { register(content = content(imageFileIds = listOf(999L))) }
    }

    @Test
    fun 다른_판매자의_상품은_PRODUCT_NOT_FOUND() {
        val productId = register()

        assertCode(ErrorCode.PRODUCT_NOT_FOUND) { query.getMine(other, productId) }
        assertCode(ErrorCode.PRODUCT_NOT_FOUND) { service.changeSalePrice(other, productId, ChangeSalePriceCommand(8000)) }
        assertCode(ErrorCode.PRODUCT_NOT_FOUND) { service.pause(other, productId) }
        assertEquals(0, query.listMine(other, null, 0, 20).totalElements)
    }

    @Test
    fun 판매가_변경은_즉시_반영된다() {
        val productId = register()

        service.changeSalePrice(me, productId, ChangeSalePriceCommand(5000))

        val detail = query.getMine(me, productId)
        assertEquals(5000, detail.salePrice)
        assertEquals(10000, detail.originalPrice)
        assertEquals(50, detail.discountRate)
        assertCode(ErrorCode.PRODUCT_INVALID_PRICE) { service.changeSalePrice(me, productId, ChangeSalePriceCommand(10001)) }
    }

    @Test
    fun 직접_수정_시_valueId를_유지한_값의_SKU는_그대로다() {
        val productId = register()
        val before = query.getMine(me, productId)
        val color = before.options.single { it.name == "색상" }
        val size = before.options.single { it.name == "사이즈" }
        val black = color.values.single { it.name == "블랙" }
        val medium = size.values.single { it.name == "M" }
        val blackM = before.skus.single { it.optionValueIds.toSet() == setOf(black.valueId, medium.valueId) }
        service.updateSku(me, productId, blackM.skuId, UpdateSkuCommand(extraPrice = 500, stock = 3, onSale = true))

        // 블랙 → 검정(ID 유지), 화이트 삭제, 레드 추가; 사이즈는 M 만 유지하고 L 삭제.
        service.editContent(
            me, productId,
            content(
                options = listOf(
                    OptionCommand(color.optionId, "색상", listOf(OptionValueCommand(black.valueId, "검정"), OptionValueCommand(null, "레드"))),
                    OptionCommand(size.optionId, "사이즈", listOf(OptionValueCommand(medium.valueId, "M"))),
                ),
            ),
        )

        val after = query.getMine(me, productId)
        val kept = after.skus.single { it.skuId == blackM.skuId }
        assertEquals("검정 / M", kept.displayName)
        assertEquals(3, kept.stock)
        assertEquals(500L, kept.extraPrice)
        assertEquals("ACTIVE", kept.skuStatus)
        assertEquals(2, after.skus.count { it.skuStatus == "ACTIVE" })
        assertEquals(3, after.skus.count { it.skuStatus == "DISCONTINUED" })
        assertEquals(5, after.skus.size)
        assertEquals("PENDING_REVIEW", after.status)
        assertTrue(after.skus.single { it.displayName == "레드 / M" }.skuStatus == "ACTIVE")
    }

    @Test
    fun 목록_size가_100을_넘으면_INVALID_REQUEST() {
        assertCode(ErrorCode.INVALID_REQUEST) { query.listMine(me, null, 0, 101) }
    }
}
