package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.ProductChangeRequest
import com.dogyun.kairos.catalog.domain.ProductChangeRequestRepository
import com.dogyun.kairos.catalog.domain.ProductId
import com.dogyun.kairos.catalog.domain.ProductRepository
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.security.SellerPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 판매자의 상품 변경 요청 제출·철회. 상품은 승인될 때까지 그대로다. */
@Service
@Transactional
class ChangeRequestService(
    private val productRepository: ProductRepository,
    private val changeRequestRepository: ProductChangeRequestRepository,
    private val categoryQueryService: CategoryQueryService,
    private val imageFileReader: ImageFileReader,
) {

    /** ON_SALE·PAUSED 상품만, 대기 중인 요청은 상품당 하나. 검증(카테고리·이미지·값 ID·정가)은 제출 시점에 한 번 미리 한다. */
    fun submit(seller: SellerPrincipal, productId: Long, command: SubmitChangeRequestCommand): Long {
        val product = productRepository.findByIdAndSellerId(ProductId(productId), seller.sellerId)
            ?: throw DomainException(ErrorCode.PRODUCT_NOT_FOUND)
        val content = validatedContent(seller, command.content, categoryQueryService, imageFileReader)
        product.validateChange(content, command.originalPrice)
        if (changeRequestRepository.findPendingByProductId(productId) != null) {
            throw DomainException(ErrorCode.CHANGE_REQUEST_ALREADY_PENDING)
        }
        val request = ProductChangeRequest.submit(productId, seller.sellerId, content, command.originalPrice)
        return requireNotNull(changeRequestRepository.save(request).id)
    }

    /** 남의 요청·다른 상품의 요청은 404. */
    fun withdraw(seller: SellerPrincipal, productId: Long, changeRequestId: Long) {
        val request = changeRequestRepository.findById(changeRequestId)
            ?.takeIf { it.sellerId == seller.sellerId && it.productId == productId }
            ?: throw DomainException(ErrorCode.CHANGE_REQUEST_NOT_FOUND)
        request.withdraw()
        changeRequestRepository.save(request)
    }
}
