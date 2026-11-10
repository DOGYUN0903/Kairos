package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.ProductId
import com.dogyun.kairos.catalog.domain.ProductLikeRepository
import com.dogyun.kairos.catalog.domain.ProductRepository
import com.dogyun.kairos.catalog.domain.ProductStatsRepository
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

data class LikedProductResult(
    val productId: Long,
    val name: String,
    val brandName: String,
    val thumbnailUrl: String,
    val salePrice: Long,
    val productStatus: String,
    val likedAt: LocalDateTime,
)

/**
 * 좋아요는 PUT/DELETE 멱등이다. like_count 는 `product_like` 행의 상태가 실제로 바뀐 경우에만 ±1 하므로
 * 같은 회원의 동시 요청이 몰려도 집계가 실제 좋아요 행 수와 어긋나지 않는다. 락 순서는 product_like → product.
 */
@Service
class ProductLikeService(
    private val productRepository: ProductRepository,
    private val likeRepository: ProductLikeRepository,
    private val statsRepository: ProductStatsRepository,
    private val imageFileReader: ImageFileReader,
) {

    @Transactional
    fun like(memberId: Long, productId: Long) {
        requireProduct(productId)
        if (likeRepository.like(productId, memberId)) statsRepository.addLikeCount(productId, 1)
    }

    @Transactional
    fun unlike(memberId: Long, productId: Long) {
        requireProduct(productId)
        if (likeRepository.unlike(productId, memberId)) statsRepository.addLikeCount(productId, -1)
    }

    @Transactional(readOnly = true)
    fun listMine(memberId: Long, page: Int, size: Int): PageResult<LikedProductResult> {
        if (page < 0 || size !in 1..MAX_SIZE) throw DomainException(ErrorCode.INVALID_REQUEST)
        val result = likeRepository.findPageLiked(memberId, page, size)
        val urls = if (result.content.isEmpty()) emptyMap()
        else imageFileReader.findImages(result.content.map { it.mainFileId }.toSet()).mapValues { it.value.url }
        return PageResult(
            content = result.content.map {
                LikedProductResult(it.productId, it.name, it.brandName, urls[it.mainFileId].orEmpty(), it.salePrice, it.productStatus, it.likedAt)
            },
            page = result.page,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext,
        )
    }

    private fun requireProduct(productId: Long) {
        productRepository.findById(ProductId(productId)) ?: throw DomainException(ErrorCode.PRODUCT_NOT_FOUND)
    }

    private companion object {
        const val MAX_SIZE = 100
    }
}
