package com.dogyun.kairos.seller.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.seller.domain.Brand
import com.dogyun.kairos.seller.domain.BrandId
import com.dogyun.kairos.seller.domain.BrandRepository
import com.dogyun.kairos.seller.domain.BrandStatus
import com.dogyun.kairos.seller.domain.ReviewHistory
import com.dogyun.kairos.seller.domain.ReviewHistoryRepository
import com.dogyun.kairos.seller.domain.ReviewResult
import com.dogyun.kairos.seller.domain.ReviewTarget
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

/** 관리자의 브랜드 심사. 상태가 바뀔 때마다 심사 이력을 한 건 남긴다. */
@Service
class BrandAdminService(
    private val brandRepository: BrandRepository,
    private val reviewHistoryRepository: ReviewHistoryRepository,
    private val imageFileReader: ImageFileReader,
    private val clock: Clock,
) {

    @Transactional
    fun approve(brandId: Long, reviewerId: Long) =
        review(brandId, ReviewResult.APPROVED, null, reviewerId) { it.approve() }

    @Transactional
    fun reject(brandId: Long, reason: String, reviewerId: Long) =
        review(brandId, ReviewResult.REJECTED, reason, reviewerId) { it.reject(reason) }

    @Transactional(readOnly = true)
    fun listForAdmin(status: BrandStatus?, page: Int, size: Int): PageResult<BrandDetail> {
        if (page < 0 || size !in 1..MAX_SIZE) throw DomainException(ErrorCode.INVALID_REQUEST)
        val result = brandRepository.findPage(status, page, size)
        return PageResult(
            content = toBrandDetails(result.content, imageFileReader),
            page = result.page,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext,
        )
    }

    private fun review(
        brandId: Long,
        result: ReviewResult,
        reason: String?,
        reviewerId: Long,
        transition: (Brand) -> Unit,
    ) {
        val brand = brandRepository.findById(BrandId(brandId)) ?: throw DomainException(ErrorCode.BRAND_NOT_FOUND)
        transition(brand)
        brandRepository.save(brand)
        reviewHistoryRepository.save(
            ReviewHistory(null, ReviewTarget.BRAND, brandId, result, reason, reviewerId, LocalDateTime.now(clock)),
        )
    }

    private companion object {
        const val MAX_SIZE = 100
    }
}
