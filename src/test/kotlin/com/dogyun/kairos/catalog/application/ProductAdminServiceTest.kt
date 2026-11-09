package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.Product
import com.dogyun.kairos.catalog.domain.ProductContent
import com.dogyun.kairos.catalog.domain.ProductId
import com.dogyun.kairos.catalog.domain.ProductRepository
import com.dogyun.kairos.catalog.domain.ProductReviewHistory
import com.dogyun.kairos.catalog.domain.ProductReviewHistoryRepository
import com.dogyun.kairos.catalog.domain.ProductReviewResult
import com.dogyun.kairos.catalog.domain.ProductReviewTarget
import com.dogyun.kairos.catalog.domain.ProductStatus
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProductAdminServiceTest {

    private class FakeProductRepository : ProductRepository {
        val store = linkedMapOf<Long, Product>()
        private var sequence = 0L

        override fun save(product: Product): Product {
            val id = product.id ?: ProductId(++sequence)
            val saved = Product.restore(
                id, product.sellerId, product.brandId, product.content, product.originalPrice, product.salePrice,
                product.status, product.rejectReason, product.onSaleAt, product.salesCount, product.skus,
            )
            store[id.value] = saved
            return saved
        }

        override fun findById(id: ProductId): Product? = store[id.value]
        override fun findByIdAndSellerId(id: ProductId, sellerId: Long): Product? =
            store[id.value]?.takeIf { it.sellerId == sellerId }

        override fun findPageBySellerId(sellerId: Long, status: ProductStatus?, page: Int, size: Int): PageResult<Product> =
            page(store.values.filter { it.sellerId == sellerId && (status == null || it.status == status) }, page, size)

        override fun findPageByStatus(status: ProductStatus?, page: Int, size: Int): PageResult<Product> =
            page(store.values.filter { status == null || it.status == status }, page, size)

        override fun existsByCategoryId(categoryId: Long): Boolean = store.values.any { it.content.categoryId == categoryId }

        private fun page(all: List<Product>, page: Int, size: Int): PageResult<Product> {
            val ordered = all.reversed()
            return PageResult.of(ordered.drop(page * size).take(size), page, size, ordered.size.toLong())
        }
    }

    private class FakeHistoryRepository : ProductReviewHistoryRepository {
        val store = mutableListOf<ProductReviewHistory>()
        override fun save(history: ProductReviewHistory): ProductReviewHistory {
            val saved = ProductReviewHistory(
                (store.size + 1).toLong(), history.target, history.targetId, history.result,
                history.reason, history.reviewerId, history.reviewedAt,
            )
            store.add(saved)
            return saved
        }

        override fun findAllByTarget(target: ProductReviewTarget, targetId: Long) =
            store.filter { it.target == target && it.targetId == targetId }
    }

    private class FakeSellerReader : SellerReader {
        override fun findBrand(brandId: Long): BrandSnapshot? = null
        override fun getBrandNames(brandIds: Collection<Long>): Map<Long, String> = brandIds.associateWith { "키로스" }
    }

    private class FakeImageFileReader : ImageFileReader {
        override fun findImages(fileIds: Collection<Long>): Map<Long, ImageFileRef> =
            fileIds.associateWith { ImageFileRef(it, "http://files/$it.png", 1L, true) }
    }

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 11, 9, 10, 0)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)
    private val products = FakeProductRepository()
    private val histories = FakeHistoryRepository()
    private val service = ProductAdminService(products, histories, FakeSellerReader(), FakeImageFileReader(), clock)
    private val reviewerId = 99L

    private fun content(name: String = "티셔츠") = ProductContent(name, "설명", 5L, listOf(100L), emptyList())

    private fun pendingProduct(name: String = "티셔츠"): Long =
        requireNotNull(products.save(Product.register(1L, 10L, content(name), 10000, 9000)).id).value

    private fun assertCode(expected: ErrorCode, block: () -> Unit) {
        assertEquals(expected, assertFailsWith<DomainException> { block() }.errorCode)
    }

    @Test
    fun 승인하면_ON_SALE_onSaleAt_이력() {
        val id = pendingProduct()

        service.approve(id, reviewerId)

        val product = requireNotNull(products.findById(ProductId(id)))
        assertEquals(ProductStatus.ON_SALE, product.status)
        assertEquals(now, product.onSaleAt)
        val history = histories.findAllByTarget(ProductReviewTarget.PRODUCT, id).single()
        assertEquals(ProductReviewResult.APPROVED, history.result)
        assertNull(history.reason)
        assertEquals(reviewerId, history.reviewerId)
        assertEquals(now, history.reviewedAt)
    }

    @Test
    fun 반려하면_REJECTED_사유_이력() {
        val id = pendingProduct()

        service.reject(id, "이미지가 흐립니다", reviewerId)

        val product = requireNotNull(products.findById(ProductId(id)))
        assertEquals(ProductStatus.REJECTED, product.status)
        assertEquals("이미지가 흐립니다", product.rejectReason)
        assertNull(product.onSaleAt)
        val history = histories.findAllByTarget(ProductReviewTarget.PRODUCT, id).single()
        assertEquals(ProductReviewResult.REJECTED, history.result)
        assertEquals("이미지가 흐립니다", history.reason)
    }

    @Test
    fun onSaleAt은_첫_승인_시각이고_이후_변경_요청_승인에서도_유지된다() {
        val id = pendingProduct()
        service.approve(id, reviewerId)
        val product = requireNotNull(products.findById(ProductId(id)))

        product.applyChange(content("새 이름"), 12000)

        assertEquals(now, product.onSaleAt)
        assertEquals(ProductStatus.ON_SALE, product.status)
        assertEquals("새 이름", product.content.name)
    }

    @Test
    fun 반려_후_다시_승인하면_첫_승인_시각이_기록된다() {
        val id = pendingProduct()
        service.reject(id, "사유", reviewerId)
        val rejected = requireNotNull(products.findById(ProductId(id)))
        rejected.editContent(content("고침"))
        products.save(rejected)

        service.approve(id, reviewerId)

        val product = requireNotNull(products.findById(ProductId(id)))
        assertEquals(ProductStatus.ON_SALE, product.status)
        assertNull(product.rejectReason)
        assertEquals(now, product.onSaleAt)
        assertEquals(
            listOf(ProductReviewResult.REJECTED, ProductReviewResult.APPROVED),
            histories.findAllByTarget(ProductReviewTarget.PRODUCT, id).map { it.result },
        )
    }

    @Test
    fun ON_SALE_상품_재승인은_INVALID_STATE_TRANSITION() {
        val id = pendingProduct()
        service.approve(id, reviewerId)

        assertCode(ErrorCode.PRODUCT_INVALID_STATE_TRANSITION) { service.approve(id, reviewerId) }
        assertCode(ErrorCode.PRODUCT_INVALID_STATE_TRANSITION) { service.reject(id, "사유", reviewerId) }
        assertEquals(1, histories.store.size)
    }

    @Test
    fun 없는_상품은_PRODUCT_NOT_FOUND() {
        assertCode(ErrorCode.PRODUCT_NOT_FOUND) { service.approve(999L, reviewerId) }
        assertCode(ErrorCode.PRODUCT_NOT_FOUND) { service.reject(999L, "사유", reviewerId) }
        assertCode(ErrorCode.PRODUCT_NOT_FOUND) { service.getForAdmin(999L) }
        assertEquals(0, histories.store.size)
    }

    @Test
    fun 목록은_상태로_거르고_브랜드명과_썸네일을_채운다() {
        val first = pendingProduct("첫째")
        pendingProduct("둘째")
        service.approve(first, reviewerId)

        val onSale = service.listForAdmin(ProductStatus.ON_SALE, 0, 20)
        val all = service.listForAdmin(null, 0, 20)

        assertEquals(listOf("첫째"), onSale.content.map { it.name })
        assertEquals("키로스", onSale.content.single().brandName)
        assertEquals("http://files/100.png", onSale.content.single().thumbnailUrl)
        assertEquals(2, all.totalElements)
        assertCode(ErrorCode.INVALID_REQUEST) { service.listForAdmin(null, 0, 101) }
    }
}
