package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.Product
import com.dogyun.kairos.catalog.domain.ProductChangeRequestRepository
import com.dogyun.kairos.catalog.domain.ProductId
import com.dogyun.kairos.catalog.domain.ProductRepository
import com.dogyun.kairos.catalog.domain.ProductReviewHistory
import com.dogyun.kairos.catalog.domain.ProductReviewHistoryRepository
import com.dogyun.kairos.catalog.domain.ProductReviewResult
import com.dogyun.kairos.catalog.domain.ProductReviewTarget
import com.dogyun.kairos.catalog.domain.ProductStatus
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

data class AdminProductSummary(
    val productId: Long,
    val sellerId: Long,
    val brandName: String,
    val name: String,
    val thumbnailUrl: String,
    val originalPrice: Long,
    val salePrice: Long,
    val status: String,
)

/** 관리자의 상품 심사. 승인·반려할 때마다 심사 이력을 한 건 남긴다. */
@Service
class ProductAdminService(
    private val productRepository: ProductRepository,
    private val changeRequestRepository: ProductChangeRequestRepository,
    private val historyRepository: ProductReviewHistoryRepository,
    private val sellerReader: SellerReader,
    private val imageFileReader: ImageFileReader,
    private val clock: Clock,
) {

    @Transactional
    fun approve(productId: Long, reviewerId: Long) {
        val now = LocalDateTime.now(clock)
        review(productId, ProductReviewResult.APPROVED, null, reviewerId, now) { it.approve(now) }
    }

    @Transactional
    fun reject(productId: Long, reason: String, reviewerId: Long) {
        review(productId, ProductReviewResult.REJECTED, reason, reviewerId, LocalDateTime.now(clock)) { it.reject(reason) }
    }

    @Transactional(readOnly = true)
    fun listForAdmin(status: ProductStatus?, page: Int, size: Int): PageResult<AdminProductSummary> {
        if (page < 0 || size !in 1..MAX_SIZE) throw DomainException(ErrorCode.INVALID_REQUEST)
        val result = productRepository.findPageByStatus(status, page, size)
        val products = result.content
        val brandNames = sellerReader.getBrandNames(products.map { it.brandId }.toSet())
        val urls = imageFileReader.findImages(products.map { it.content.imageFileIds.first() }).mapValues { it.value.url }
        val summaries = products.map {
            val thumbnailId = it.content.imageFileIds.first()
            AdminProductSummary(
                productId = requireNotNull(it.id).value,
                sellerId = it.sellerId,
                brandName = brandNames[it.brandId].orEmpty(),
                name = it.content.name,
                thumbnailUrl = urls[thumbnailId].orEmpty(),
                originalPrice = it.originalPrice,
                salePrice = it.salePrice,
                status = it.status.name,
            )
        }
        return PageResult(summaries, result.page, result.size, result.totalElements, result.totalPages, result.hasNext)
    }

    @Transactional(readOnly = true)
    fun getForAdmin(productId: Long): SellerProductDetail {
        val product = productRepository.findById(ProductId(productId)) ?: throw DomainException(ErrorCode.PRODUCT_NOT_FOUND)
        val brandName = sellerReader.getBrandNames(listOf(product.brandId))[product.brandId].orEmpty()
        val urls = imageFileReader.findImages(product.content.imageFileIds).mapValues { it.value.url }
        val pending = changeRequestRepository.findPendingByProductId(productId)?.id
        return toSellerProductDetail(product, brandName, urls, pending)
    }

    private fun review(
        productId: Long,
        result: ProductReviewResult,
        reason: String?,
        reviewerId: Long,
        reviewedAt: LocalDateTime,
        transition: (Product) -> Unit,
    ) {
        val product = productRepository.findById(ProductId(productId)) ?: throw DomainException(ErrorCode.PRODUCT_NOT_FOUND)
        transition(product)
        productRepository.save(product)
        historyRepository.save(
            ProductReviewHistory(null, ProductReviewTarget.PRODUCT, productId, result, reason, reviewerId, reviewedAt),
        )
    }

    private companion object {
        const val MAX_SIZE = 100
    }
}
