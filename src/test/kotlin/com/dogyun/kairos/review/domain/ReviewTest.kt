package com.dogyun.kairos.review.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReviewTest {

    private fun review(rating: Int = 5, content: String = "열 글자 이상 되는 후기", images: List<Long> = emptyList()) =
        Review.create(orderItemId = 1L, productId = 2L, productName = "티셔츠", memberId = 3L, sellerId = 4L, rating = rating, content = content, imageFileIds = images)

    private fun assertInvalid(block: () -> Unit) {
        assertEquals(ErrorCode.REVIEW_INVALID, assertFailsWith<DomainException> { block() }.errorCode)
    }

    @Test
    fun 별점_0이나_6은_REVIEW_INVALID() {
        assertInvalid { review(rating = 0) }
        assertInvalid { review(rating = 6) }
        assertInvalid { review().edit(6, "열 글자 이상 되는 후기", emptyList()) }
    }

    @Test
    fun 본문_9자는_REVIEW_INVALID() {
        assertInvalid { review(content = "123456789") }
        assertInvalid { review(content = "   123456789   ") }
        assertEquals("1234567890", review(content = "1234567890").content)
    }

    @Test
    fun 사진_4장은_REVIEW_INVALID() {
        assertInvalid { review(images = listOf(1L, 2L, 3L, 4L)) }
        assertInvalid { review(images = listOf(1L, 1L)) }
        val r = review(images = listOf(7L, 8L, 9L))
        assertTrue(r.hasPhoto)
        assertEquals(listOf(ReviewImage(7L, 0), ReviewImage(8L, 1), ReviewImage(9L, 2)), r.images)
    }

    @Test
    fun grantReward는_한_번만_된다() {
        val r = review()
        r.grantReward(100L)
        assertEquals(100L, r.rewardPoints)
        assertFailsWith<IllegalStateException> { r.grantReward(500L) }
    }

    @Test
    fun reply는_덮어쓴다() {
        val r = review()
        r.reply("감사합니다", java.time.LocalDateTime.of(2026, 11, 10, 10, 0))
        r.reply("다시 감사합니다", java.time.LocalDateTime.of(2026, 11, 11, 10, 0))
        assertEquals("다시 감사합니다", r.replyContent)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 11, 10, 0), r.repliedAt)
        assertInvalid { r.reply("", java.time.LocalDateTime.now()) }
        assertInvalid { r.reply("가".repeat(501), java.time.LocalDateTime.now()) }
    }
}
