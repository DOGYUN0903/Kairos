package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.LikedProductRow
import com.dogyun.kairos.catalog.domain.Product
import com.dogyun.kairos.catalog.domain.ProductContent
import com.dogyun.kairos.catalog.domain.ProductLikeRepository
import com.dogyun.kairos.catalog.domain.ProductId
import com.dogyun.kairos.catalog.domain.ProductListRow
import com.dogyun.kairos.catalog.domain.ProductSearchCondition
import com.dogyun.kairos.catalog.domain.ProductSearchRepository
import com.dogyun.kairos.catalog.domain.ProductStatsRepository
import com.dogyun.kairos.catalog.domain.ProductStatus
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProductLikeServiceTest {

    /** (productId, memberId) → liked. 실제 저장소처럼 "상태가 바뀌었는지"를 돌려준다. */
    private class FakeLikeRepository : ProductLikeRepository {
        val liked = mutableMapOf<Pair<Long, Long>, Boolean>()

        override fun like(productId: Long, memberId: Long): Boolean {
            val key = productId to memberId
            val changed = liked[key] != true
            liked[key] = true
            return changed
        }

        override fun unlike(productId: Long, memberId: Long): Boolean {
            val key = productId to memberId
            if (liked[key] != true) return false
            liked[key] = false
            return true
        }

        override fun findLikedProductIds(memberId: Long, productIds: Collection<Long>): Set<Long> =
            productIds.filter { liked[it to memberId] == true }.toSet()

        override fun findPageLiked(memberId: Long, page: Int, size: Int): PageResult<LikedProductRow> =
            PageResult.of(emptyList(), page, size, 0)
    }

    private class FakeStatsRepository : ProductStatsRepository {
        val likeCounts = mutableMapOf<Long, Long>()

        override fun addLikeCount(productId: Long, delta: Long) {
            likeCounts.merge(productId, delta, Long::plus)
        }

        override fun addReview(productId: Long, countDelta: Long, ratingDelta: Long) = Unit
    }

    private val products = FakeProductRepository()
    private val likes = FakeLikeRepository()
    private val stats = FakeStatsRepository()
    private val imageFileReader = object : ImageFileReader {
        override fun findImages(fileIds: Collection<Long>) = emptyMap<Long, ImageFileRef>()
    }
    /** 노출 조건 = ON_SALE 만 흉내낸다(브랜드·판매자 조건은 쿼리 쪽 책임). */
    private val search = object : ProductSearchRepository {
        override fun search(condition: ProductSearchCondition, page: Int, size: Int): PageResult<ProductListRow> =
            PageResult.of(emptyList(), page, size, 0)

        override fun findExposedDetail(productId: ProductId): Product? =
            products.findById(productId)?.takeIf { it.status == ProductStatus.ON_SALE }
    }
    private val service = ProductLikeService(products, likes, stats, search, imageFileReader)

    private fun registered(): Product {
        val content = ProductContent("티셔츠", "설명", 5L, listOf(10L), emptyList())
        return products.save(Product.register(1L, 2L, content, 10000, 9000))
    }

    private fun product(): Long {
        val saved = registered()
        saved.approve(java.time.LocalDateTime.of(2026, 11, 10, 10, 0))
        return requireNotNull(products.save(saved).id).value
    }

    @Test
    fun 처음_좋아요는_like_count를_1_올린다() {
        val productId = product()

        service.like(memberId = 7L, productId = productId)

        assertEquals(1L, stats.likeCounts[productId])
    }

    @Test
    fun 이미_좋아요면_올리지_않는다() {
        val productId = product()

        service.like(7L, productId)
        service.like(7L, productId)

        assertEquals(1L, stats.likeCounts[productId])
    }

    @Test
    fun 취소는_liked일_때만_1_내린다() {
        val productId = product()

        service.unlike(7L, productId)
        assertEquals(null, stats.likeCounts[productId])

        service.like(7L, productId)
        service.unlike(7L, productId)
        service.unlike(7L, productId)

        assertEquals(0L, stats.likeCounts[productId])
    }

    @Test
    fun 노출되지_않는_상품에는_좋아요할_수_없고_집계도_그대로다() {
        val pendingId = requireNotNull(registered().id).value

        assertFailsWith<DomainException> { service.like(7L, pendingId) }.also { assertEquals(ErrorCode.PRODUCT_NOT_FOUND, it.errorCode) }

        assertEquals(null, stats.likeCounts[pendingId])
    }

    @Test
    fun 판매_종료된_상품도_좋아요_취소는_할_수_있다() {
        val productId = product()
        service.like(7L, productId)
        products.findById(com.dogyun.kairos.catalog.domain.ProductId(productId))!!.also { it.end() }

        service.unlike(7L, productId)

        assertEquals(0L, stats.likeCounts[productId])
    }

    @Test
    fun 없는_상품에_좋아요는_PRODUCT_NOT_FOUND다() {
        assertFailsWith<DomainException> { service.like(7L, 999L) }.also { assertEquals(ErrorCode.PRODUCT_NOT_FOUND, it.errorCode) }
    }

    @Test
    fun 없거나_좋아요한_적_없는_상품의_취소는_예외없이_집계도_그대로다() {
        service.unlike(7L, 999L)

        assertEquals(null, stats.likeCounts[999L])
    }

    @Test
    fun 조회되지_않는_삭제된_상품도_좋아요_행이_있으면_취소되고_1_내린다() {
        // 소프트 딜리트된 상품은 ProductRepository 로 조회되지 않는다(저장소에 없는 상태로 흉내).
        likes.liked[999L to 7L] = true

        service.unlike(7L, 999L)

        assertEquals(false, likes.liked[999L to 7L])
        assertEquals(-1L, stats.likeCounts[999L])
    }
}
