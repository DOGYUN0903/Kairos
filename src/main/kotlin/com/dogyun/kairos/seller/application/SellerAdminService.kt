package com.dogyun.kairos.seller.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.seller.domain.ReviewHistory
import com.dogyun.kairos.seller.domain.ReviewHistoryRepository
import com.dogyun.kairos.seller.domain.ReviewResult
import com.dogyun.kairos.seller.domain.ReviewTarget
import com.dogyun.kairos.seller.domain.Seller
import com.dogyun.kairos.seller.domain.SellerId
import com.dogyun.kairos.seller.domain.SellerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

/** 관리자의 판매자 심사. 상태가 바뀔 때마다 심사 이력을 한 건 남긴다. */
@Service
class SellerAdminService(
    private val sellerRepository: SellerRepository,
    private val reviewHistoryRepository: ReviewHistoryRepository,
    private val clock: Clock,
) {

    @Transactional
    fun approve(sellerId: Long, commissionRateBp: Int, reviewerId: Long) =
        review(sellerId, ReviewResult.APPROVED, null, reviewerId) { it.approve(commissionRateBp) }

    @Transactional
    fun reject(sellerId: Long, reason: String, reviewerId: Long) =
        review(sellerId, ReviewResult.REJECTED, reason, reviewerId) { it.reject(reason) }

    @Transactional
    fun suspend(sellerId: Long, reason: String, reviewerId: Long) =
        review(sellerId, ReviewResult.SUSPENDED, reason, reviewerId) { it.suspend(reason) }

    @Transactional
    fun unsuspend(sellerId: Long, reviewerId: Long) =
        review(sellerId, ReviewResult.UNSUSPENDED, null, reviewerId) { it.unsuspend() }

    private fun review(
        sellerId: Long,
        result: ReviewResult,
        reason: String?,
        reviewerId: Long,
        transition: (Seller) -> Unit,
    ) {
        val seller = sellerRepository.findById(SellerId(sellerId)) ?: throw DomainException(ErrorCode.SELLER_NOT_FOUND)
        transition(seller)
        sellerRepository.save(seller)
        reviewHistoryRepository.save(
            ReviewHistory(null, ReviewTarget.SELLER, sellerId, result, reason, reviewerId, LocalDateTime.now(clock)),
        )
    }
}
