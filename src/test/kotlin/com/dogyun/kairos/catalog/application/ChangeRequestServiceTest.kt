package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.Category
import com.dogyun.kairos.catalog.domain.CategoryId
import com.dogyun.kairos.catalog.domain.CategoryRepository
import com.dogyun.kairos.catalog.domain.ChangeRequestStatus
import com.dogyun.kairos.catalog.domain.OptionValue
import com.dogyun.kairos.catalog.domain.Product
import com.dogyun.kairos.catalog.domain.ProductContent
import com.dogyun.kairos.catalog.domain.ProductId
import com.dogyun.kairos.catalog.domain.ProductOption
import com.dogyun.kairos.catalog.domain.ProductReviewHistory
import com.dogyun.kairos.catalog.domain.ProductReviewHistoryRepository
import com.dogyun.kairos.catalog.domain.ProductReviewResult
import com.dogyun.kairos.catalog.domain.ProductReviewTarget
import com.dogyun.kairos.catalog.domain.ProductStatus
import com.dogyun.kairos.catalog.domain.SkuStatus
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.ListOrder
import com.dogyun.kairos.common.security.SellerPrincipal
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangeRequestServiceTest {

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
        override fun getSellerNames(sellerIds: Collection<Long>): Map<Long, String> = sellerIds.associateWith { "판매자$it" }
    }

    private class FakeImageFileReader(private val files: Map<Long, ImageFileRef>) : ImageFileReader {
        override fun findImages(fileIds: Collection<Long>): Map<Long, ImageFileRef> = files.filterKeys { it in fileIds }
    }

    private val me = SellerPrincipal(sellerId = 1L, memberId = 1L, canManage = true, canView = true)
    private val other = SellerPrincipal(sellerId = 2L, memberId = 2L, canManage = true, canView = true)
    private val reviewerId = 99L
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 11, 9, 10, 0)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)

    private val products = FakeProductRepository()
    private val changeRequests = FakeProductChangeRequestRepository()
    private val histories = FakeHistoryRepository()
    private val categories = FakeCategoryRepository()
    private val files = FakeImageFileReader(
        mapOf(
            100L to ImageFileRef(100L, "http://files/a.png", uploaderMemberId = 1L, isImage = true),
            101L to ImageFileRef(101L, "http://files/b.png", uploaderMemberId = 1L, isImage = true),
            200L to ImageFileRef(200L, "http://files/x.png", uploaderMemberId = 2L, isImage = true),
        ),
    )
    private val categoryQueryService = CategoryQueryService(categories)
    private val service = ChangeRequestService(products, changeRequests, categoryQueryService, files)
    private val adminService = ChangeRequestAdminService(products, changeRequests, categoryQueryService, histories, files, FakeSellerReader(), clock)
    private val query = ProductQueryService(products, changeRequests, FakeSellerReader(), files)

    private val root: Long = requireNotNull(categories.save(Category.root("패션", 1)).id).value
    private val leaf: Long = requireNotNull(categories.save(Category.child(categories.findById(CategoryId(root))!!, "상의", 1)).id).value

    private fun colorSize() = listOf(
        OptionCommand(null, "색상", listOf(OptionValueCommand(null, "블랙"), OptionValueCommand(null, "화이트"))),
        OptionCommand(null, "사이즈", listOf(OptionValueCommand(null, "M"), OptionValueCommand(null, "L"))),
    )

    private fun content(
        name: String = "티셔츠",
        categoryId: Long = leaf,
        imageFileIds: List<Long> = listOf(100L, 101L),
        options: List<OptionCommand> = colorSize(),
    ) = ProductContentCommand(name, "설명", categoryId, imageFileIds, options)

    /** 등록 → 승인 → 모든 SKU 재고 5. 정가 10000, 판매가 8000. */
    private fun onSaleProduct(): Long {
        val product = Product.register(me.sellerId, 10L, toContent(content()), 10000, 8000)
        val saved = products.save(product)
        saved.approve(now)
        saved.skus.forEach { saved.updateSku(requireNotNull(it.id), 0, 5, true) }
        return requireNotNull(products.save(saved).id).value
    }

    private fun toContent(command: ProductContentCommand) = ProductContent(
        command.name, command.description, command.categoryId, command.imageFileIds,
        command.options.map { o -> ProductOption(o.optionId, o.name, o.values.map { OptionValue(it.valueId, it.name) }) },
    )

    private fun product(id: Long): Product = requireNotNull(products.findById(ProductId(id)))

    /** 색상[블랙(유지)·레드(새 값)] × 사이즈[M·L(유지)]: 화이트 조합이 사라지고 레드 조합이 새로 생긴다. */
    private fun blackRedCommand(productId: Long, name: String = "새 티셔츠", originalPrice: Long = 10000): SubmitChangeRequestCommand {
        val options = product(productId).content.options
        val color = options[0]
        val size = options[1]
        return SubmitChangeRequestCommand(
            content(
                name = name,
                options = listOf(
                    OptionCommand(color.id, "색상", listOf(OptionValueCommand(color.values[0].id, "블랙"), OptionValueCommand(null, "레드"))),
                    OptionCommand(size.id, "사이즈", size.values.map { OptionValueCommand(it.id, it.name) }),
                ),
            ),
            originalPrice,
        )
    }

    private fun submitSame(productId: Long, originalPrice: Long = 10000): Long =
        service.submit(me, productId, SubmitChangeRequestCommand(content(name = "새 티셔츠"), originalPrice))

    private fun assertCode(expected: ErrorCode, block: () -> Unit) {
        assertEquals(expected, assertFailsWith<DomainException> { block() }.errorCode)
    }

    @Test
    fun ON_SALE_상품의_변경_요청은_PENDING으로_저장되고_상품은_그대로다() {
        val productId = onSaleProduct()

        val id = submitSame(productId)

        val request = requireNotNull(changeRequests.findById(id))
        assertEquals(ChangeRequestStatus.PENDING, request.status)
        assertEquals(productId, request.productId)
        assertEquals(me.sellerId, request.sellerId)
        assertEquals("새 티셔츠", request.content.name)
        assertEquals("티셔츠", product(productId).content.name)
        assertEquals(ProductStatus.ON_SALE, product(productId).status)
        assertEquals(id, query.getMine(me, productId).pendingChangeRequestId)
        val detail = adminService.get(id)
        assertEquals("새 티셔츠", detail.content.name)
        assertEquals(listOf("http://files/a.png", "http://files/b.png"), detail.content.images.map { it.url })
        assertEquals("PENDING", detail.status)
    }

    @Test
    fun 판매자는_자기_변경_요청을_조회하고_남의_요청이나_다른_상품의_요청은_404() {
        val productId = onSaleProduct()
        val id = service.submit(me, productId, SubmitChangeRequestCommand(content(name = "새 티셔츠"), 12000))

        val detail = service.getMine(me, productId, id)

        assertEquals(id, detail.changeRequestId)
        assertEquals("PENDING", detail.status)
        assertEquals(12000, detail.originalPrice)
        assertEquals("새 티셔츠", detail.content.name)
        assertEquals(listOf("http://files/a.png", "http://files/b.png"), detail.content.images.map { it.url })
        assertEquals(LocalDateTime.of(2026, 11, 9, 9, 0), detail.requestedAt)
        assertCode(ErrorCode.CHANGE_REQUEST_NOT_FOUND) { service.getMine(other, productId, id) }
        assertCode(ErrorCode.CHANGE_REQUEST_NOT_FOUND) { service.getMine(me, productId + 1000, id) }
        assertCode(ErrorCode.CHANGE_REQUEST_NOT_FOUND) { service.getMine(me, productId, 999L) }
    }

    @Test
    fun 판매자_상세의_latestChangeRequest는_가장_최근_요청이고_반려_사유를_담는다() {
        val productId = onSaleProduct()
        assertNull(query.getMine(me, productId).latestChangeRequest)
        val first = submitSame(productId)
        service.withdraw(me, productId, first)
        val second = submitSame(productId)
        assertEquals(second, query.getMine(me, productId).latestChangeRequest?.changeRequestId)

        adminService.reject(second, "이미지가 흐립니다", reviewerId)

        val latest = requireNotNull(query.getMine(me, productId).latestChangeRequest)
        assertEquals(second, latest.changeRequestId)
        assertEquals("REJECTED", latest.status)
        assertEquals("이미지가 흐립니다", latest.rejectReason)
        assertEquals(LocalDateTime.of(2026, 11, 9, 9, 0), latest.requestedAt)
        assertNull(query.getMine(me, productId).pendingChangeRequestId)
    }

    @Test
    fun 기존_값_ID로_이름만_바꾸면_승인_후에도_SKU와_재고가_유지된다() {
        val productId = onSaleProduct()
        val before = product(productId).skus.map { requireNotNull(it.id) }.toSet()
        val options = product(productId).content.options
        val renamed = options.map { o ->
            OptionCommand(o.id, if (o.name == "색상") "컬러" else o.name, o.values.map { OptionValueCommand(it.id, if (it.name == "블랙") "검정" else it.name) })
        }
        val id = service.submit(me, productId, SubmitChangeRequestCommand(content(options = renamed), 10000))

        adminService.approve(id, reviewerId)

        val after = product(productId)
        assertEquals("컬러", after.content.options[0].name)
        assertEquals(listOf("검정", "화이트"), after.content.options[0].values.map { it.name })
        assertEquals(before, after.skus.map { requireNotNull(it.id) }.toSet())
        assertTrue(after.skus.all { it.skuStatus == SkuStatus.ACTIVE && it.stock == 5 })
    }

    @Test
    fun 제출_후_카테고리가_삭제되면_승인은_CATEGORY_NOT_FOUND이고_요청은_PENDING으로_남는다() {
        val productId = onSaleProduct()
        val id = submitSame(productId)
        categories.delete(CategoryId(leaf))

        assertCode(ErrorCode.CATEGORY_NOT_FOUND) { adminService.approve(id, reviewerId) }

        assertEquals(ChangeRequestStatus.PENDING, requireNotNull(changeRequests.findById(id)).status)
        assertEquals("티셔츠", product(productId).content.name)
        assertTrue(histories.store.isEmpty())
    }

    @Test
    fun 대기_중인_요청이_있으면_CHANGE_REQUEST_ALREADY_PENDING() {
        val productId = onSaleProduct()
        submitSame(productId)

        assertCode(ErrorCode.CHANGE_REQUEST_ALREADY_PENDING) { submitSame(productId) }
    }

    @Test
    fun PENDING_REVIEW_상품은_변경_요청을_낼_수_없다() {
        val productId = requireNotNull(products.save(Product.register(me.sellerId, 10L, toContent(content()), 10000, 8000)).id).value

        assertCode(ErrorCode.PRODUCT_INVALID_STATE_TRANSITION) { submitSame(productId) }
        assertNull(query.getMine(me, productId).pendingChangeRequestId)
    }

    @Test
    fun 철회하면_다시_낼_수_있다() {
        val productId = onSaleProduct()
        val first = submitSame(productId)

        service.withdraw(me, productId, first)

        assertEquals(ChangeRequestStatus.WITHDRAWN, changeRequests.findById(first)?.status)
        assertNull(query.getMine(me, productId).pendingChangeRequestId)
        val second = submitSame(productId)
        assertEquals(second, query.getMine(me, productId).pendingChangeRequestId)
        assertCode(ErrorCode.CHANGE_REQUEST_INVALID_STATE_TRANSITION) { service.withdraw(me, productId, first) }
    }

    @Test
    fun 다른_판매자의_요청_철회는_CHANGE_REQUEST_NOT_FOUND() {
        val productId = onSaleProduct()
        val id = submitSame(productId)

        assertCode(ErrorCode.CHANGE_REQUEST_NOT_FOUND) { service.withdraw(other, productId, id) }
        assertCode(ErrorCode.CHANGE_REQUEST_NOT_FOUND) { service.withdraw(me, productId, 999L) }
        assertCode(ErrorCode.CHANGE_REQUEST_NOT_FOUND) { adminService.get(999L) }
        assertEquals(ChangeRequestStatus.PENDING, changeRequests.findById(id)?.status)
    }

    @Test
    fun 검증은_상품_수정과_같다_카테고리_이미지_값_ID() {
        val productId = onSaleProduct()
        val black = product(productId).content.options[0].values[0].id

        assertCode(ErrorCode.CATEGORY_NOT_LEAF) { service.submit(me, productId, SubmitChangeRequestCommand(content(categoryId = root), 10000)) }
        assertCode(ErrorCode.FILE_NOT_OWNED) { service.submit(me, productId, SubmitChangeRequestCommand(content(imageFileIds = listOf(200L)), 10000)) }
        // 기존 값을 새 옵션 아래로.
        assertCode(ErrorCode.PRODUCT_INVALID) {
            service.submit(me, productId, SubmitChangeRequestCommand(content(options = listOf(OptionCommand(null, "소재", listOf(OptionValueCommand(black, "면"))))), 10000))
        }
        assertCode(ErrorCode.PRODUCT_NOT_FOUND) { service.submit(other, productId, SubmitChangeRequestCommand(content(), 10000)) }
        assertTrue(changeRequests.store.isEmpty())
    }

    @Test
    fun 승인하면_상품_내용이_바뀌고_같은_값_ID_조합의_SKU_재고가_유지된다() {
        val productId = onSaleProduct()
        val before = product(productId)
        val blackId = requireNotNull(before.content.options[0].values[0].id)
        val whiteId = requireNotNull(before.content.options[0].values[1].id)
        val mId = requireNotNull(before.content.options[1].values[0].id)
        val blackM = before.skus.single { it.optionValueIds == listOf(blackId, mId).sorted() }
        val id = service.submit(me, productId, blackRedCommand(productId))

        adminService.approve(id, reviewerId)

        val after = product(productId)
        assertEquals("새 티셔츠", after.content.name)
        assertEquals(ProductStatus.ON_SALE, after.status)
        assertEquals(ChangeRequestStatus.APPROVED, changeRequests.findById(id)?.status)
        assertNull(query.getMine(me, productId).pendingChangeRequestId)
        val redId = requireNotNull(after.content.options[0].values.single { it.name == "레드" }.id)
        val keptBlackM = after.skus.single { it.id == blackM.id }
        assertEquals(5, keptBlackM.stock)
        assertEquals(SkuStatus.ACTIVE, keptBlackM.skuStatus)
        val redM = after.skus.single { it.optionValueIds == listOf(redId, mId).sorted() }
        assertEquals(0, redM.stock)
        assertEquals(SkuStatus.ACTIVE, redM.skuStatus)
        val whiteSkus = after.skus.filter { whiteId in it.optionValueIds }
        assertEquals(2, whiteSkus.size)
        assertTrue(whiteSkus.all { it.skuStatus == SkuStatus.DISCONTINUED })
        assertEquals(4, after.activeSkus.size)
        val history = histories.findAllByTarget(ProductReviewTarget.CHANGE_REQUEST, id).single()
        assertEquals(ProductReviewResult.APPROVED, history.result)
        assertEquals(reviewerId, history.reviewerId)
        assertEquals(now, history.reviewedAt)
    }

    @Test
    fun 승인해도_onSaleAt은_바뀌지_않는다() {
        val productId = onSaleProduct()
        val onSaleAt = product(productId).onSaleAt
        val id = submitSame(productId)

        adminService.approve(id, reviewerId)

        assertEquals(onSaleAt, product(productId).onSaleAt)
        assertEquals(ProductStatus.ON_SALE, product(productId).status)
    }

    @Test
    fun 정가를_바꾼_변경_요청은_승인_전까지_정가가_그대로고_승인하면_바뀐다() {
        val productId = onSaleProduct()
        val id = submitSame(productId, originalPrice = 16000)
        assertEquals(10000, product(productId).originalPrice)
        assertEquals(16000, adminService.get(id).originalPrice)

        adminService.approve(id, reviewerId)

        assertEquals(16000, product(productId).originalPrice)
        assertEquals(8000, product(productId).salePrice)
        assertEquals(50, product(productId).discountRate)
    }

    @Test
    fun 새_정가가_판매가보다_작으면_PRODUCT_INVALID_PRICE() {
        val productId = onSaleProduct()

        assertCode(ErrorCode.PRODUCT_INVALID_PRICE) { submitSame(productId, originalPrice = 7999) }
        assertTrue(changeRequests.store.isEmpty())

        // 제출 뒤 판매가가 올라 새 정가를 넘으면 승인 시점에 막힌다. 요청은 PENDING 으로 남는다.
        val id = submitSame(productId, originalPrice = 9000)
        val product = product(productId)
        product.changeSalePrice(9500)
        products.save(product)
        assertCode(ErrorCode.PRODUCT_INVALID_PRICE) { adminService.approve(id, reviewerId) }
        assertEquals(ChangeRequestStatus.PENDING, changeRequests.findById(id)?.status)
        assertEquals(10000, product(productId).originalPrice)
    }

    @Test
    fun 반려하면_상품은_그대로다() {
        val productId = onSaleProduct()
        val id = service.submit(me, productId, blackRedCommand(productId))

        adminService.reject(id, "옵션 구성이 맞지 않습니다", reviewerId)

        val request = requireNotNull(changeRequests.findById(id))
        assertEquals(ChangeRequestStatus.REJECTED, request.status)
        assertEquals("옵션 구성이 맞지 않습니다", request.rejectReason)
        val product = product(productId)
        assertEquals("티셔츠", product.content.name)
        assertEquals(4, product.activeSkus.size)
        assertTrue(product.skus.all { it.skuStatus == SkuStatus.ACTIVE && it.stock == 5 })
        assertNull(query.getMine(me, productId).pendingChangeRequestId)
        val history = histories.findAllByTarget(ProductReviewTarget.CHANGE_REQUEST, id).single()
        assertEquals(ProductReviewResult.REJECTED, history.result)
        assertEquals("옵션 구성이 맞지 않습니다", history.reason)
        assertCode(ErrorCode.CHANGE_REQUEST_INVALID_STATE_TRANSITION) { adminService.approve(id, reviewerId) }
        assertEquals(1, histories.store.size)
    }

    @Test
    fun 종료된_상품의_변경_요청은_승인할_수_없다() {
        val productId = onSaleProduct()
        val id = submitSame(productId)
        val product = product(productId)
        product.end()
        products.save(product)

        assertCode(ErrorCode.PRODUCT_INVALID_STATE_TRANSITION) { adminService.approve(id, reviewerId) }

        assertEquals(ChangeRequestStatus.PENDING, changeRequests.findById(id)?.status)
        assertEquals(ProductStatus.ENDED, product(productId).status)
        assertEquals("티셔츠", product(productId).content.name)
        assertTrue(histories.store.isEmpty())
    }

    @Test
    fun 관리자_목록은_상태로_거르고_최신순이다() {
        val first = onSaleProduct()
        val second = onSaleProduct()
        val firstRequest = submitSame(first)
        val secondRequest = submitSame(second)
        adminService.reject(firstRequest, "사유", reviewerId)

        val pending = adminService.listForAdmin(ChangeRequestStatus.PENDING, ListOrder.LATEST, 0, 20)
        val all = adminService.listForAdmin(null, ListOrder.LATEST, 0, 20)

        assertEquals(listOf(secondRequest), pending.content.map { it.changeRequestId })
        assertEquals(listOf(secondRequest, firstRequest), all.content.map { it.changeRequestId })
        assertEquals("새 티셔츠", all.content.first().name)
        assertCode(ErrorCode.INVALID_REQUEST) { adminService.listForAdmin(null, ListOrder.LATEST, 0, 101) }
    }
}
