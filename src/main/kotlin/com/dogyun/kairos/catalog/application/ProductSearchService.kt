package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.ProductId
import com.dogyun.kairos.catalog.domain.ProductListRow
import com.dogyun.kairos.catalog.domain.ProductSearchCondition
import com.dogyun.kairos.catalog.domain.ProductSearchRepository
import com.dogyun.kairos.catalog.domain.ProductSort
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 비로그인 사용자 상품 목록·검색·상세. 노출 조건은 [ProductSearchRepository] 가 쿼리로 건다. */
@Service
@Transactional(readOnly = true)
class ProductSearchService(
    private val searchRepository: ProductSearchRepository,
    private val categoryQueryService: CategoryQueryService,
    private val sellerReader: SellerReader,
    private val imageFileReader: ImageFileReader,
) {

    fun list(
        categoryId: Long?,
        brandId: Long?,
        minPrice: Long?,
        maxPrice: Long?,
        keyword: String?,
        sort: ProductSort,
        page: Int,
        size: Int,
    ): PageResult<ProductSummary> {
        if (page < 0 || size !in 1..MAX_SIZE) throw DomainException(ErrorCode.INVALID_REQUEST)
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) throw DomainException(ErrorCode.INVALID_REQUEST)
        val leafCategoryIds = categoryId?.let { categoryQueryService.leafIdsUnder(it) }
        // 소분류가 하나도 없는 대분류: 상품이 있을 수 없으므로 쿼리 없이 빈 페이지.
        if (leafCategoryIds != null && leafCategoryIds.isEmpty()) return PageResult.of(emptyList(), page, size, 0)

        val condition = ProductSearchCondition(
            leafCategoryIds = leafCategoryIds,
            brandId = brandId,
            minPrice = minPrice,
            maxPrice = maxPrice,
            keyword = keyword?.trim()?.ifEmpty { null },
            sort = sort,
        )
        val result = searchRepository.search(condition, page, size)
        return PageResult(
            content = toSummaries(result.content),
            page = result.page,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext,
        )
    }

    fun detail(productId: Long): ProductDetail {
        val product = searchRepository.findExposedDetail(ProductId(productId))
            ?: throw DomainException(ErrorCode.PRODUCT_NOT_FOUND)
        val brandName = sellerReader.getBrandNames(listOf(product.brandId))[product.brandId].orEmpty()
        val urls = imageFileReader.findImages(product.content.imageFileIds).mapValues { it.value.url }
        return toProductDetail(product, brandName, urls)
    }

    /** 대표 이미지 URL 을 페이지 단위로 한 번에 조회한다. */
    private fun toSummaries(rows: List<ProductListRow>): List<ProductSummary> {
        if (rows.isEmpty()) return emptyList()
        val urls = imageFileReader.findImages(rows.map { it.mainFileId }.toSet()).mapValues { it.value.url }
        return rows.map { toProductSummary(it, urls[it.mainFileId].orEmpty()) }
    }

    private companion object {
        const val MAX_SIZE = 100
    }
}
