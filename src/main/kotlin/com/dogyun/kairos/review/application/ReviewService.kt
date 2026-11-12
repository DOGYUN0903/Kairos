package com.dogyun.kairos.review.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.review.domain.Review
import com.dogyun.kairos.review.domain.ReviewRepository
import com.dogyun.kairos.review.domain.reviewWritableUntil
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

/**
 * 후기 작성·수정·삭제·답글. 상품 집계는 같은 트랜잭션 안의 증감 UPDATE 로 바꾼다.
 *
 * 락 순서(ADR 0004): 작성은 적립(`member_point`)을 먼저, 집계(`product` 행)를 나중에 한다. 취소 마무리가
 * `member_point → … → product(decreaseSales)` 순서라 반대로 잡으면 교착한다.
 * 수정·삭제·답글은 후기 행을 FOR UPDATE 로 먼저 읽고(`review → product`) 그 현재 값으로 판단한다.
 */
@Service
@Transactional
class ReviewService(
    private val reviewRepository: ReviewRepository,
    private val orderItemReader: OrderItemReader,
    private val imageFileReader: ReviewImageFileReader,
    private val ratingUpdater: ProductRatingUpdater,
    private val rewardGranter: ReviewRewardGranter,
    private val properties: ReviewProperties,
    private val clock: Clock,
) {

    fun write(memberId: Long, orderId: Long, orderItemId: Long, cmd: WriteReviewCommand): WriteReviewResult {
        val item = orderItemReader.findConfirmedItem(memberId, orderItemId)?.takeIf { it.orderId == orderId }
            ?: throw DomainException(ErrorCode.ORDER_ITEM_NOT_FOUND)
        val confirmedAt = item.confirmedAt ?: throw DomainException(ErrorCode.REVIEW_NOT_ELIGIBLE)
        if (reviewWritableUntil(confirmedAt, properties.writeDeadline).isBefore(now())) throw DomainException(ErrorCode.REVIEW_NOT_ELIGIBLE)

        val review = Review.create(
            orderItemId, item.productId, item.productName, memberId, item.sellerId, cmd.rating, cmd.content, cmd.imageFileIds,
        )
        requireOwnImages(memberId, cmd.imageFileIds)
        // 빠른 거절용 일반 읽기. 동시 요청이 둘 다 통과해도 UNIQUE(order_item_id) 가 하나만 남긴다.
        if (reviewRepository.existsAnyByOrderItemId(orderItemId)) throw DomainException(ErrorCode.REVIEW_ALREADY_WRITTEN)
        val saved = try {
            reviewRepository.save(review)
        } catch (e: DataIntegrityViolationException) {
            throw DomainException(ErrorCode.REVIEW_ALREADY_WRITTEN)
        }
        val reviewId = requireNotNull(saved.id)

        val reward = rewardGranter.grantForReview(memberId, reviewId, saved.hasPhoto)
        if (reward > 0) {
            saved.grantReward(reward)
            reviewRepository.save(saved)
        }
        ratingUpdater.applyReview(item.productId, 1, saved.rating.toLong())
        return WriteReviewResult(reviewId, reward)
    }

    /** 집계는 별점 차이만 반영한다. 적립은 하지 않는다(사진을 뒤늦게 붙여도). */
    fun edit(memberId: Long, reviewId: Long, cmd: WriteReviewCommand) {
        val review = reviewRepository.findByIdForUpdate(reviewId)?.takeIf { it.memberId == memberId }
            ?: throw DomainException(ErrorCode.REVIEW_NOT_FOUND)
        val oldRating = review.rating
        review.edit(cmd.rating, cmd.content, cmd.imageFileIds)
        requireOwnImages(memberId, cmd.imageFileIds)
        reviewRepository.save(review)
        val delta = (review.rating - oldRating).toLong()
        if (delta != 0L) ratingUpdater.applyReview(review.productId, 0, delta)
    }

    /** 집계에서 빼고 소프트 삭제한다. 적립금은 회수하지 않는다. */
    fun delete(memberId: Long, reviewId: Long) {
        val review = reviewRepository.findByIdForUpdate(reviewId)?.takeIf { it.memberId == memberId }
            ?: throw DomainException(ErrorCode.REVIEW_NOT_FOUND)
        if (!reviewRepository.delete(review)) throw DomainException(ErrorCode.REVIEW_NOT_FOUND)
        ratingUpdater.applyReview(review.productId, -1, -review.rating.toLong())
    }

    /** 판매자 답글(덮어쓰기, 멱등). 남의 상품 후기는 없는 것으로 본다. */
    fun reply(sellerId: Long, reviewId: Long, content: String) {
        val review = reviewRepository.findByIdForUpdate(reviewId)?.takeIf { it.sellerId == sellerId }
            ?: throw DomainException(ErrorCode.REVIEW_NOT_FOUND)
        review.reply(content, now())
        reviewRepository.save(review)
    }

    private fun requireOwnImages(memberId: Long, fileIds: List<Long>) {
        if (fileIds.isEmpty()) return
        val found = imageFileReader.findImages(fileIds)
        val ok = fileIds.all { id -> found[id]?.let { it.isImage && it.uploaderMemberId == memberId } == true }
        if (!ok) throw DomainException(ErrorCode.REVIEW_INVALID)
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock)
}
