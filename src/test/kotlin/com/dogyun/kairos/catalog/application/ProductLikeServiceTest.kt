package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.LikedProductRow
import com.dogyun.kairos.catalog.domain.Product
import com.dogyun.kairos.catalog.domain.ProductContent
import com.dogyun.kairos.catalog.domain.ProductLikeRepository
import com.dogyun.kairos.catalog.domain.ProductStatsRepository
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
    private val service = ProductLikeService(products, likes, stats, imageFileReader)

    private fun product(): Long {
        val content = ProductContent("티셔츠", "설명", 5L, listOf(10L), emptyList())
        return requireNotNull(products.save(Product.register(1L, 2L, content, 10000, 9000)).id).value
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
    fun 없는_상품은_PRODUCT_NOT_FOUND() {
        assertFailsWith<DomainException> { service.like(7L, 999L) }.also { assertEquals(ErrorCode.PRODUCT_NOT_FOUND, it.errorCode) }
        assertFailsWith<DomainException> { service.unlike(7L, 999L) }.also { assertEquals(ErrorCode.PRODUCT_NOT_FOUND, it.errorCode) }
    }
}
