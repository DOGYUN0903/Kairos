package com.dogyun.kairos.review.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.review.domain.Review
import com.dogyun.kairos.review.domain.ReviewRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 후기 목록·단건 조회. 삭제된 후기는 보이지 않는다. 작성자 이름은 마스킹한다. */
@Service
@Transactional(readOnly = true)
class ReviewQueryService(
    private val reviewRepository: ReviewRepository,
    private val imageFileReader: ReviewImageFileReader,
    private val nameReader: ReviewerNameReader,
) {

    fun listByProduct(productId: Long, page: Int, size: Int): PageResult<ReviewResult> {
        validate(page, size)
        return toResults(reviewRepository.findPageByProductId(productId, page, size))
    }

    fun listMine(memberId: Long, page: Int, size: Int): PageResult<ReviewResult> {
        validate(page, size)
        return toResults(reviewRepository.findPageByMemberId(memberId, page, size))
    }

    fun listForSeller(sellerId: Long, page: Int, size: Int): PageResult<ReviewResult> {
        validate(page, size)
        return toResults(reviewRepository.findPageBySellerId(sellerId, page, size))
    }

    fun get(memberId: Long, reviewId: Long): ReviewResult {
        val review = reviewRepository.findById(reviewId)?.takeIf { it.memberId == memberId }
            ?: throw DomainException(ErrorCode.REVIEW_NOT_FOUND)
        return toResults(PageResult.of(listOf(review), 0, 1, 1)).content.single()
    }

    private fun toResults(page: PageResult<Review>): PageResult<ReviewResult> {
        val reviews = page.content
        val urls = imageFileReader.findImages(reviews.flatMap { r -> r.images.map { it.fileId } }.toSet()).mapValues { it.value.url }
        val names = if (reviews.isEmpty()) emptyMap() else nameReader.findMaskedNames(reviews.map { it.memberId }.toSet())
        return page.map { r ->
            ReviewResult(
                reviewId = requireNotNull(r.id),
                orderItemId = r.orderItemId,
                productId = r.productId,
                productName = r.productName,
                rating = r.rating,
                content = r.content,
                imageUrls = r.images.sortedBy { it.displayOrder }.mapNotNull { urls[it.fileId] },
                writerName = names[r.memberId] ?: UNKNOWN_WRITER,
                replyContent = r.replyContent,
                repliedAt = r.repliedAt,
                rewardPoints = r.rewardPoints,
                createdAt = requireNotNull(r.createdAt),
                modifiedAt = requireNotNull(r.modifiedAt),
            )
        }
    }

    private fun <T, R> PageResult<T>.map(transform: (T) -> R) =
        PageResult(content.map(transform), page, size, totalElements, totalPages, hasNext)

    private fun validate(page: Int, size: Int) {
        if (page < 0 || size !in 1..MAX_SIZE) throw DomainException(ErrorCode.INVALID_REQUEST)
    }

    private companion object {
        const val MAX_SIZE = 100
        const val UNKNOWN_WRITER = "탈퇴 회원"
    }
}
