package com.dogyun.kairos.review.infrastructure

import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.review.domain.Review
import com.dogyun.kairos.review.domain.ReviewImage
import com.dogyun.kairos.review.domain.ReviewRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class ReviewRepositoryImpl(private val jpaRepository: ReviewJpaRepository) : ReviewRepository {

    /** 새 후기는 바로 flush 해 `UNIQUE(order_item_id)` 위반을 이 호출에서 `DataIntegrityViolationException` 으로 드러낸다. */
    override fun save(review: Review): Review {
        val entity = review.id?.let { id ->
            requireNotNull(jpaRepository.findByIdOrNull(id)) { "후기 $id 가 없다." }
        } ?: ReviewEntity(review.orderItemId, review.productId, review.productName, review.memberId, review.sellerId, review.rating, review.content)
        entity.update(review.rating, review.content, review.replyContent, review.repliedAt, review.rewardPoints)
        entity.replaceImages(review.images.map { it.fileId to it.displayOrder })
        return toDomain(jpaRepository.saveAndFlush(entity))
    }

    override fun findById(id: Long): Review? = jpaRepository.findByIdOrNull(id)?.let(::toDomain)

    override fun findByIdForUpdate(id: Long): Review? = jpaRepository.findLockedById(id)?.let(::toDomain)

    override fun existsAnyByOrderItemId(orderItemId: Long): Boolean = jpaRepository.countAnyByOrderItemId(orderItemId) > 0

    /** 호출자는 [findByIdForUpdate] 로 행을 잠근 뒤 부른다. 잠근 행이 ACTIVE 면 이번 호출이 지운 것이다. */
    override fun delete(review: Review): Boolean {
        val entity = jpaRepository.findByIdOrNull(requireNotNull(review.id)) ?: return false
        entity.softDelete()
        jpaRepository.flush()
        return true
    }

    override fun findPageByProductId(productId: Long, page: Int, size: Int): PageResult<Review> =
        jpaRepository.findAllByProductId(productId, latest(page, size)).toResult()

    override fun findPageByMemberId(memberId: Long, page: Int, size: Int): PageResult<Review> =
        jpaRepository.findAllByMemberId(memberId, latest(page, size)).toResult()

    override fun findPageBySellerId(sellerId: Long, page: Int, size: Int): PageResult<Review> =
        jpaRepository.findAllBySellerId(sellerId, latest(page, size)).toResult()

    private fun latest(page: Int, size: Int) = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))

    private fun Page<ReviewEntity>.toResult(): PageResult<Review> =
        PageResult.of(content.map(::toDomain), number, size, totalElements)

    private fun toDomain(e: ReviewEntity) = Review(
        id = e.id,
        orderItemId = e.orderItemId,
        productId = e.productId,
        productName = e.productName,
        memberId = e.memberId,
        sellerId = e.sellerId,
        rating = e.rating,
        content = e.content,
        images = e.activeImages().sortedBy { it.displayOrder }.map { ReviewImage(it.fileId, it.displayOrder) },
        replyContent = e.replyContent,
        repliedAt = e.repliedAt,
        rewardPoints = e.rewardPoints,
        createdAt = e.createdAt,
        modifiedAt = e.modifiedAt,
    )
}
