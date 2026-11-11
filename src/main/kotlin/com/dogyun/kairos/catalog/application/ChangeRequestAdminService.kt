package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.ChangeRequestStatus
import com.dogyun.kairos.catalog.domain.ChangedField
import com.dogyun.kairos.catalog.domain.ProductChangeRequest
import com.dogyun.kairos.catalog.domain.ProductChangeRequestRepository
import com.dogyun.kairos.catalog.domain.ProductId
import com.dogyun.kairos.catalog.domain.ProductRepository
import com.dogyun.kairos.catalog.domain.ProductReviewHistory
import com.dogyun.kairos.catalog.domain.ProductReviewHistoryRepository
import com.dogyun.kairos.catalog.domain.ProductReviewResult
import com.dogyun.kairos.catalog.domain.ProductReviewTarget
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.ListOrder
import com.dogyun.kairos.common.domain.PageResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

/** 관리자의 변경 요청 심사. 승인·반려할 때마다 심사 이력(CHANGE_REQUEST)을 한 건 남긴다. */
@Service
class ChangeRequestAdminService(
    private val productRepository: ProductRepository,
    private val changeRequestRepository: ProductChangeRequestRepository,
    private val categoryQueryService: CategoryQueryService,
    private val historyRepository: ProductReviewHistoryRepository,
    private val imageFileReader: ImageFileReader,
    private val sellerReader: SellerReader,
    private val clock: Clock,
) {

    /**
     * 변경 요청(ProductChangeRequest)과 상품(Product) 두 애그리거트를 한 트랜잭션에서 바꾼다.
     * 같은 모듈 안의 의도적 예외다: 승인은 "요청이 APPROVED 가 되는 것"과 "상품에 반영되는 것"이 하나의 사실이어야 하고,
     * 둘 중 하나만 남으면 판매자가 본 심사 결과와 실제 상품이 어긋난다. ADR 은 두지 않는다.
     * 상품이 ENDED 면 applyChange 가 PRODUCT_INVALID_STATE_TRANSITION 을 던지므로 요청은 PENDING 으로 남는다.
     */
    @Transactional
    fun approve(changeRequestId: Long, reviewerId: Long) {
        val request = find(changeRequestId)
        if (request.status != ChangeRequestStatus.PENDING) throw DomainException(ErrorCode.CHANGE_REQUEST_INVALID_STATE_TRANSITION)
        val product = productRepository.findById(ProductId(request.productId)) ?: throw DomainException(ErrorCode.PRODUCT_NOT_FOUND)
        // 제출 이후 카테고리가 삭제됐을 수 있으므로 승인 시점에 다시 확인한다.
        categoryQueryService.requireLeaf(request.content.categoryId)
        product.applyChange(request.content, request.originalPrice)
        productRepository.save(product)
        request.approve()
        changeRequestRepository.save(request)
        saveHistory(changeRequestId, ProductReviewResult.APPROVED, null, reviewerId)
    }

    @Transactional
    fun reject(changeRequestId: Long, reason: String, reviewerId: Long) {
        val request = find(changeRequestId)
        request.reject(reason)
        changeRequestRepository.save(request)
        saveHistory(changeRequestId, ProductReviewResult.REJECTED, reason, reviewerId)
    }

    @Transactional(readOnly = true)
    fun listForAdmin(status: ChangeRequestStatus?, order: ListOrder, page: Int, size: Int): PageResult<ChangeRequestSummary> {
        if (page < 0 || size !in 1..MAX_SIZE) throw DomainException(ErrorCode.INVALID_REQUEST)
        val result = changeRequestRepository.findPage(status, order, page, size)
        val sellerNames = sellerReader.getSellerNames(result.content.map { it.sellerId }.toSet())
        val products = productRepository.findAllByIds(result.content.map { it.productId }.toSet()).associateBy { it.id?.value }
        val summaries = result.content.map {
            ChangeRequestSummary(
                changeRequestId = requireNotNull(it.id),
                productId = it.productId,
                sellerId = it.sellerId,
                name = it.content.name,
                originalPrice = it.originalPrice,
                status = it.status.name,
                requestedAt = requireNotNull(it.createdAt) { "저장소에서 읽은 변경 요청에는 요청 일시가 있어야 합니다" },
                sellerName = sellerNames[it.sellerId] ?: DELETED_SELLER_NAME,
                changedFields = products[it.productId]?.let { product -> it.changedFields(product).map(ChangedField::name) }.orEmpty(),
            )
        }
        return PageResult(summaries, result.page, result.size, result.totalElements, result.totalPages, result.hasNext)
    }

    @Transactional(readOnly = true)
    fun get(changeRequestId: Long): ChangeRequestDetail {
        val request = find(changeRequestId)
        val content = request.content
        val urls = imageFileReader.findImages(content.imageFileIds).mapValues { it.value.url }
        return ChangeRequestDetail(
            changeRequestId = changeRequestId,
            productId = request.productId,
            sellerId = request.sellerId,
            status = request.status.name,
            requestedAt = requireNotNull(request.createdAt) { "저장소에서 읽은 변경 요청에는 요청 일시가 있어야 합니다" },
            rejectReason = request.rejectReason,
            originalPrice = request.originalPrice,
            content = toContentResult(content, urls),
        )
    }

    private fun find(changeRequestId: Long): ProductChangeRequest =
        changeRequestRepository.findById(changeRequestId) ?: throw DomainException(ErrorCode.CHANGE_REQUEST_NOT_FOUND)

    private fun saveHistory(changeRequestId: Long, result: ProductReviewResult, reason: String?, reviewerId: Long) {
        historyRepository.save(
            ProductReviewHistory(
                null, ProductReviewTarget.CHANGE_REQUEST, changeRequestId, result, reason, reviewerId, LocalDateTime.now(clock),
            ),
        )
    }

    private companion object {
        const val MAX_SIZE = 100
    }
}
