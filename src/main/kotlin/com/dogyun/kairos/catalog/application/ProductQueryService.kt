package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.Product
import com.dogyun.kairos.catalog.domain.ProductId
import com.dogyun.kairos.catalog.domain.ProductRepository
import com.dogyun.kairos.catalog.domain.ProductStatus
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.common.security.SellerPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ProductQueryService(
    private val productRepository: ProductRepository,
    private val sellerReader: SellerReader,
    private val imageFileReader: ImageFileReader,
) {

    fun getMine(seller: SellerPrincipal, productId: Long): SellerProductDetail {
        val product = productRepository.findByIdAndSellerId(ProductId(productId), seller.sellerId)
            ?: throw DomainException(ErrorCode.PRODUCT_NOT_FOUND)
        val brandName = sellerReader.getBrandNames(listOf(product.brandId))[product.brandId].orEmpty()
        val urls = imageFileReader.findImages(product.content.imageFileIds).mapValues { it.value.url }
        return toSellerProductDetail(product, brandName, urls)
    }

    fun listMine(seller: SellerPrincipal, status: ProductStatus?, page: Int, size: Int): PageResult<SellerProductSummary> {
        if (page < 0 || size !in 1..MAX_SIZE) throw DomainException(ErrorCode.INVALID_REQUEST)
        val result = productRepository.findPageBySellerId(seller.sellerId, status, page, size)
        return PageResult(
            content = toSummaries(result.content),
            page = result.page,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext,
        )
    }

    /** 브랜드명과 대표 이미지 URL 을 상품 목록 단위로 한 번씩 조회한다. */
    private fun toSummaries(products: List<Product>): List<SellerProductSummary> {
        val brandNames = sellerReader.getBrandNames(products.map { it.brandId }.toSet())
        val urls = imageFileReader.findImages(products.map { it.content.imageFileIds.first() }).mapValues { it.value.url }
        return products.map {
            toSellerProductSummary(it, brandNames[it.brandId].orEmpty(), urls[it.content.imageFileIds.first()].orEmpty())
        }
    }

    private companion object {
        const val MAX_SIZE = 100
    }
}
