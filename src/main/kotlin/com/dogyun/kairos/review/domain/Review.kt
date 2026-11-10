package com.dogyun.kairos.review.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.LocalDateTime

/**
 * 구매 확정된 주문 항목 하나에 대한 후기. 판매자 답글은 같은 애그리거트의 값(답글 하나, 덮어쓰기)이다.
 * [productName] 은 작성 시점 주문 항목의 상품명 스냅샷이다(목록에서 catalog 를 다시 부르지 않으려고).
 */
class Review(
    val id: Long?,
    val orderItemId: Long,
    val productId: Long,
    val productName: String,
    val memberId: Long,
    val sellerId: Long,
    rating: Int,
    content: String,
    images: List<ReviewImage>,
    replyContent: String?,
    repliedAt: LocalDateTime?,
    rewardPoints: Long,
    val createdAt: LocalDateTime?,
    val modifiedAt: LocalDateTime?,
) {
    var rating: Int = rating
        private set
    var content: String = content
        private set
    var images: List<ReviewImage> = images
        private set
    var replyContent: String? = replyContent
        private set
    var repliedAt: LocalDateTime? = repliedAt
        private set
    var rewardPoints: Long = rewardPoints
        private set

    val hasPhoto: Boolean get() = images.isNotEmpty()

    fun edit(rating: Int, content: String, imageFileIds: List<Long>) {
        val trimmed = validate(rating, content, imageFileIds)
        this.rating = rating
        this.content = trimmed
        this.images = imagesOf(imageFileIds)
    }

    fun reply(content: String, now: LocalDateTime) {
        val trimmed = content.trim()
        if (trimmed.length !in 1..MAX_REPLY) throw DomainException(ErrorCode.REVIEW_INVALID)
        replyContent = trimmed
        repliedAt = now
    }

    /** 적립은 작성 시 한 번뿐이다(수정으로 사진을 붙여도 더 주지 않는다). */
    fun grantReward(points: Long) {
        check(rewardPoints == 0L) { "이미 적립한 후기다." }
        require(points > 0) { "적립액은 양수여야 한다." }
        rewardPoints = points
    }

    companion object {
        const val MIN_CONTENT = 10
        const val MAX_CONTENT = 1000
        const val MAX_REPLY = 500
        const val MAX_IMAGES = 3

        fun create(
            orderItemId: Long,
            productId: Long,
            productName: String,
            memberId: Long,
            sellerId: Long,
            rating: Int,
            content: String,
            imageFileIds: List<Long>,
        ): Review {
            val trimmed = validate(rating, content, imageFileIds)
            return Review(
                null, orderItemId, productId, productName, memberId, sellerId, rating, trimmed, imagesOf(imageFileIds),
                null, null, 0L, null, null,
            )
        }

        private fun validate(rating: Int, content: String, imageFileIds: List<Long>): String {
            val trimmed = content.trim()
            if (rating !in 1..5 ||
                trimmed.length !in MIN_CONTENT..MAX_CONTENT ||
                imageFileIds.size > MAX_IMAGES ||
                imageFileIds.toSet().size != imageFileIds.size
            ) {
                throw DomainException(ErrorCode.REVIEW_INVALID)
            }
            return trimmed
        }

        private fun imagesOf(fileIds: List<Long>) = fileIds.mapIndexed { i, id -> ReviewImage(id, i) }
    }
}
