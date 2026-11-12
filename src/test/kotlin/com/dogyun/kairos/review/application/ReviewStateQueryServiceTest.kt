package com.dogyun.kairos.review.application

import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.review.domain.Review
import com.dogyun.kairos.review.domain.ReviewRepository
import com.dogyun.kairos.review.domain.ReviewRowState
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReviewStateQueryServiceTest {

    private class FakeStateRepository : ReviewRepository {
        val rows = mutableMapOf<Long, ReviewRowState>()
        var stateCalls = 0

        override fun findStatesByOrderItemIds(ids: Collection<Long>): Map<Long, ReviewRowState> {
            stateCalls++
            return rows.filterKeys { it in ids }
        }

        override fun save(review: Review): Review = TODO()
        override fun findById(id: Long): Review? = TODO()
        override fun findByIdForUpdate(id: Long): Review? = TODO()
        override fun existsAnyByOrderItemId(orderItemId: Long): Boolean = TODO()
        override fun delete(review: Review): Boolean = TODO()
        override fun findPageByProductId(productId: Long, page: Int, size: Int): PageResult<Review> = TODO()
        override fun findPageByMemberId(memberId: Long, page: Int, size: Int): PageResult<Review> = TODO()
        override fun findPageBySellerId(sellerId: Long, page: Int, size: Int): PageResult<Review> = TODO()
    }

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 11, 10, 12, 0, 0)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)
    private val repository = FakeStateRepository()
    private val service = ReviewStateQueryService(repository, ReviewProperties(Duration.ofDays(90)), clock)

    @Test
    fun 후기가_없고_기한_안이면_WRITABLE이고_writableUntil은_확정_시각에_기한을_더한_값이다() {
        val confirmedAt = now.minusDays(1)

        val state = service.statesOf(listOf(11L to confirmedAt)).getValue(11L)

        assertEquals("WRITABLE", state.status)
        assertNull(state.reviewId)
        assertEquals(confirmedAt.plusDays(90), state.writableUntil)
    }

    @Test
    fun writableUntil_시각까지는_WRITABLE이고_1초_뒤는_EXPIRED다() {
        val atLimit = now.minusDays(90)
        val pastLimit = now.minusDays(90).minusSeconds(1)

        val states = service.statesOf(listOf(1L to atLimit, 2L to pastLimit))

        assertEquals("WRITABLE", states.getValue(1L).status)
        assertEquals("EXPIRED", states.getValue(2L).status)
        assertEquals(pastLimit.plusDays(90), states.getValue(2L).writableUntil)
    }

    @Test
    fun 후기가_있으면_WRITTEN이고_reviewId가_있다() {
        repository.rows[11L] = ReviewRowState(55L, deleted = false)

        val state = service.statesOf(listOf(11L to now.minusDays(200))).getValue(11L)

        assertEquals("WRITTEN", state.status)
        assertEquals(55L, state.reviewId)
        assertNull(state.writableUntil)
    }

    @Test
    fun 삭제한_후기는_DELETED다() {
        repository.rows[11L] = ReviewRowState(55L, deleted = true)

        val state = service.statesOf(listOf(11L to now.minusDays(1))).getValue(11L)

        assertEquals("DELETED", state.status)
        assertNull(state.reviewId)
    }

    @Test
    fun 후보가_여러_개여도_저장소는_한_번만_조회한다() {
        service.statesOf(listOf(1L to now, 2L to now, 3L to now))

        assertEquals(1, repository.stateCalls)
    }
}
