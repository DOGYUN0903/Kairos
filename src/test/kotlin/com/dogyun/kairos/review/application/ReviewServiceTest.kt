package com.dogyun.kairos.review.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.review.domain.Review
import com.dogyun.kairos.review.domain.ReviewRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReviewServiceTest {

    /** 실제 저장소처럼 삭제한 행도 order_item_id 를 차지한다(UNIQUE 가 소프트 삭제 행에도 걸림). */
    private class FakeReviewRepository : ReviewRepository {
        val rows = mutableMapOf<Long, Review>()
        val deleted = mutableSetOf<Long>()
        private var seq = 0L

        override fun save(review: Review): Review {
            val id = review.id ?: ++seq
            val saved = Review(
                id, review.orderItemId, review.productId, review.productName, review.memberId, review.sellerId, review.rating,
                review.content, review.images, review.replyContent, review.repliedAt, review.rewardPoints,
                review.createdAt ?: NOW, NOW,
            )
            rows[id] = saved
            return saved
        }

        override fun findById(id: Long): Review? = rows[id]?.takeIf { id !in deleted }?.let(::copy)
        override fun findByIdForUpdate(id: Long): Review? = findById(id)
        override fun existsAnyByOrderItemId(orderItemId: Long): Boolean = rows.values.any { it.orderItemId == orderItemId }
        override fun findStatesByOrderItemIds(ids: Collection<Long>): Map<Long, com.dogyun.kairos.review.domain.ReviewRowState> =
            rows.values.filter { it.orderItemId in ids }
                .associate { it.orderItemId to com.dogyun.kairos.review.domain.ReviewRowState(requireNotNull(it.id), it.id in deleted) }
        override fun delete(review: Review): Boolean = deleted.add(requireNotNull(review.id))
        override fun findPageByProductId(productId: Long, page: Int, size: Int) = page { it.productId == productId }
        override fun findPageByMemberId(memberId: Long, page: Int, size: Int) = page { it.memberId == memberId }
        override fun findPageBySellerId(sellerId: Long, page: Int, size: Int) = page { it.sellerId == sellerId }

        private fun page(filter: (Review) -> Boolean): PageResult<Review> {
            val content = rows.values.filter { it.id !in deleted && filter(it) }.sortedByDescending { it.id }
            return PageResult.of(content, 0, 20, content.size.toLong())
        }

        private fun copy(r: Review) = Review(
            r.id, r.orderItemId, r.productId, r.productName, r.memberId, r.sellerId, r.rating, r.content, r.images,
            r.replyContent, r.repliedAt, r.rewardPoints, r.createdAt, r.modifiedAt,
        )
    }

    private class FakeOrderItemReader : OrderItemReader {
        val items = mutableMapOf<Long, Pair<Long, ConfirmedOrderItem>>() // orderItemId → (memberId, item)
        override fun findConfirmedItem(memberId: Long, orderItemId: Long): ConfirmedOrderItem? =
            items[orderItemId]?.takeIf { it.first == memberId }?.second
    }

    private class FakeImageFileReader : ReviewImageFileReader {
        val files = mutableMapOf<Long, ReviewImageRef>()
        override fun findImages(fileIds: Collection<Long>): Map<Long, ReviewImageRef> = files.filterKeys { it in fileIds }
    }

    private class FakeRatingUpdater : ProductRatingUpdater {
        val counts = mutableMapOf<Long, Long>()
        val sums = mutableMapOf<Long, Long>()
        override fun applyReview(productId: Long, countDelta: Long, ratingDelta: Long) {
            counts.merge(productId, countDelta, Long::plus)
            sums.merge(productId, ratingDelta, Long::plus)
        }
    }

    /** 원장 키 (REVIEW, reviewId) 멱등을 흉내 낸다. */
    private class FakeRewardGranter : ReviewRewardGranter {
        val ledger = mutableMapOf<Long, Long>() // reviewId → amount
        override fun grantForReview(memberId: Long, reviewId: Long, hasPhoto: Boolean): Long {
            if (reviewId in ledger) return 0L
            val amount = if (hasPhoto) 500L else 100L
            ledger[reviewId] = amount
            return amount
        }
    }

    private val reviews = FakeReviewRepository()
    private val orderItems = FakeOrderItemReader()
    private val images = FakeImageFileReader()
    private val ratings = FakeRatingUpdater()
    private val rewards = FakeRewardGranter()
    private val clock = Clock.fixed(Instant.parse("2026-11-10T01:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val service = ReviewService(reviews, orderItems, images, ratings, rewards, ReviewProperties(Duration.ofDays(90)), clock)

    private fun confirmedItem(
        memberId: Long = MEMBER,
        orderItemId: Long = 11L,
        productId: Long = PRODUCT,
        confirmedAt: LocalDateTime? = NOW.minusDays(1),
    ) {
        orderItems.items[orderItemId] = memberId to ConfirmedOrderItem(ORDER, orderItemId, productId, SELLER, "티셔츠", confirmedAt)
    }

    private fun cmd(rating: Int = 4, imageFileIds: List<Long> = emptyList()) = WriteReviewCommand(rating, "열 글자 이상 되는 후기 본문", imageFileIds)

    private fun assertError(code: ErrorCode, block: () -> Unit) {
        assertEquals(code, kotlin.test.assertFailsWith<DomainException> { block() }.errorCode)
    }

    @Test
    fun 확정_항목에_쓰면_저장되고_집계_plus1_plus별점이고_텍스트_적립_100이다() {
        confirmedItem()
        val result = service.write(MEMBER, ORDER, 11L, cmd(rating = 4))
        val saved = reviews.findById(result.reviewId)!!
        assertEquals(4, saved.rating)
        assertEquals(PRODUCT, saved.productId)
        assertEquals(SELLER, saved.sellerId)
        assertEquals(100L, saved.rewardPoints)
        assertEquals(100L, result.rewardPoints)
        assertEquals(1L, ratings.counts[PRODUCT])
        assertEquals(4L, ratings.sums[PRODUCT])
        assertEquals(mapOf(result.reviewId to 100L), rewards.ledger)
    }

    @Test
    fun 사진이_있으면_500이_한_번_적립된다() {
        confirmedItem()
        images.files[7L] = ReviewImageRef(7L, "u7", MEMBER, true)
        val result = service.write(MEMBER, ORDER, 11L, cmd(imageFileIds = listOf(7L)))
        assertEquals(500L, result.rewardPoints)
        assertEquals(500L, reviews.findById(result.reviewId)!!.rewardPoints)
        assertEquals(1, rewards.ledger.size)
    }

    @Test
    fun 미확정_항목은_REVIEW_NOT_ELIGIBLE() {
        confirmedItem(confirmedAt = null)
        assertError(ErrorCode.REVIEW_NOT_ELIGIBLE) { service.write(MEMBER, ORDER, 11L, cmd()) }
        assertEquals(emptyMap(), ratings.counts)
    }

    @Test
    fun 확정_후_91일은_REVIEW_NOT_ELIGIBLE() {
        confirmedItem(confirmedAt = NOW.minusDays(91))
        assertError(ErrorCode.REVIEW_NOT_ELIGIBLE) { service.write(MEMBER, ORDER, 11L, cmd()) }
        confirmedItem(orderItemId = 12L, confirmedAt = NOW.minusDays(90))
        service.write(MEMBER, ORDER, 12L, cmd())
    }

    @Test
    fun 작성_API의_판정과_같다() {
        val stateService = ReviewStateQueryService(reviews, ReviewProperties(Duration.ofDays(90)), clock)
        val atLimit = NOW.minusDays(90)
        val pastLimit = NOW.minusDays(90).minusSeconds(1)
        confirmedItem(orderItemId = 21L, confirmedAt = atLimit)
        confirmedItem(orderItemId = 22L, confirmedAt = pastLimit)

        val states = stateService.statesOf(listOf(21L to atLimit, 22L to pastLimit))

        assertEquals("WRITABLE", states.getValue(21L).status)
        service.write(MEMBER, ORDER, 21L, cmd())
        assertEquals("EXPIRED", states.getValue(22L).status)
        assertError(ErrorCode.REVIEW_NOT_ELIGIBLE) { service.write(MEMBER, ORDER, 22L, cmd()) }
    }

    @Test
    fun 같은_항목_두_번은_REVIEW_ALREADY_WRITTEN() {
        confirmedItem()
        service.write(MEMBER, ORDER, 11L, cmd())
        assertError(ErrorCode.REVIEW_ALREADY_WRITTEN) { service.write(MEMBER, ORDER, 11L, cmd()) }
        assertEquals(1L, ratings.counts[PRODUCT])
        assertEquals(1, rewards.ledger.size)
    }

    @Test
    fun 삭제한_항목에_다시_쓰면_REVIEW_ALREADY_WRITTEN() {
        confirmedItem()
        val id = service.write(MEMBER, ORDER, 11L, cmd()).reviewId
        service.delete(MEMBER, id)
        assertError(ErrorCode.REVIEW_ALREADY_WRITTEN) { service.write(MEMBER, ORDER, 11L, cmd()) }
        assertEquals(1, rewards.ledger.size)
    }

    @Test
    fun 남의_주문_항목은_ORDER_ITEM_NOT_FOUND() {
        confirmedItem(memberId = 99L)
        assertError(ErrorCode.ORDER_ITEM_NOT_FOUND) { service.write(MEMBER, ORDER, 11L, cmd()) }
        confirmedItem()
        assertError(ErrorCode.ORDER_ITEM_NOT_FOUND) { service.write(MEMBER, ORDER + 1, 11L, cmd()) }
        assertError(ErrorCode.ORDER_ITEM_NOT_FOUND) { service.write(MEMBER, ORDER, 404L, cmd()) }
    }

    @Test
    fun 남이_올린_이미지는_REVIEW_INVALID() {
        confirmedItem()
        images.files[7L] = ReviewImageRef(7L, "u7", 99L, true)
        images.files[8L] = ReviewImageRef(8L, "u8", MEMBER, false)
        assertError(ErrorCode.REVIEW_INVALID) { service.write(MEMBER, ORDER, 11L, cmd(imageFileIds = listOf(7L))) }
        assertError(ErrorCode.REVIEW_INVALID) { service.write(MEMBER, ORDER, 11L, cmd(imageFileIds = listOf(8L))) }
        assertError(ErrorCode.REVIEW_INVALID) { service.write(MEMBER, ORDER, 11L, cmd(imageFileIds = listOf(404L))) }
        assertEquals(emptyMap(), rewards.ledger)
    }

    @Test
    fun 수정은_별점_차이만_집계하고_적립하지_않는다() {
        confirmedItem()
        images.files[7L] = ReviewImageRef(7L, "u7", MEMBER, true)
        val id = service.write(MEMBER, ORDER, 11L, cmd(rating = 2)).reviewId
        service.edit(MEMBER, id, cmd(rating = 5, imageFileIds = listOf(7L)))
        val edited = reviews.findById(id)!!
        assertEquals(5, edited.rating)
        assertEquals(1, edited.images.size)
        assertEquals(100L, edited.rewardPoints)
        assertEquals(1L, ratings.counts[PRODUCT])
        assertEquals(5L, ratings.sums[PRODUCT])
        assertEquals(mapOf(id to 100L), rewards.ledger)
        assertError(ErrorCode.REVIEW_NOT_FOUND) { service.edit(99L, id, cmd()) }
    }

    @Test
    fun 삭제는_집계_minus1_minus별점이고_적립을_회수하지_않는다() {
        confirmedItem()
        val id = service.write(MEMBER, ORDER, 11L, cmd(rating = 3)).reviewId
        assertError(ErrorCode.REVIEW_NOT_FOUND) { service.delete(99L, id) }
        service.delete(MEMBER, id)
        assertNull(reviews.findById(id))
        assertEquals(0L, ratings.counts[PRODUCT])
        assertEquals(0L, ratings.sums[PRODUCT])
        assertEquals(mapOf(id to 100L), rewards.ledger)
        assertError(ErrorCode.REVIEW_NOT_FOUND) { service.delete(MEMBER, id) }
        assertEquals(0L, ratings.counts[PRODUCT])
    }

    private companion object {
        const val MEMBER = 1L
        const val ORDER = 100L
        const val PRODUCT = 200L
        const val SELLER = 300L
        val NOW: LocalDateTime = LocalDateTime.of(2026, 11, 10, 10, 0)
    }
}
