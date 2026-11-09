package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.Product
import com.dogyun.kairos.catalog.domain.ProductId
import com.dogyun.kairos.catalog.domain.ProductRepository
import com.dogyun.kairos.catalog.domain.SkuId
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.security.SellerPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 판매자의 상품 등록·직접 수정·즉시 반영(판매가·SKU)·판매 상태 전이. 상품은 항상 내 것(sellerId)만 찾는다. */
@Service
@Transactional
class ProductService(
    private val productRepository: ProductRepository,
    private val sellerReader: SellerReader,
    private val categoryQueryService: CategoryQueryService,
    private val imageFileReader: ImageFileReader,
) {

    fun register(seller: SellerPrincipal, command: RegisterProductCommand): Long {
        val brand = sellerReader.findBrand(command.brandId)?.takeIf { it.sellerId == seller.sellerId }
            ?: throw DomainException(ErrorCode.BRAND_NOT_FOUND)
        if (!brand.approved) throw DomainException(ErrorCode.BRAND_NOT_APPROVED)
        val content = validatedContent(seller, command.content, categoryQueryService, imageFileReader)
        val product = Product.register(seller.sellerId, brand.brandId, content, command.originalPrice, command.salePrice)
        return requireNotNull(productRepository.save(product).id).value
    }

    fun editContent(seller: SellerPrincipal, productId: Long, command: ProductContentCommand) {
        val product = findMine(seller, productId)
        val content = validatedContent(seller, command, categoryQueryService, imageFileReader)
        product.editContent(content)
        productRepository.save(product)
    }

    fun changeSalePrice(seller: SellerPrincipal, productId: Long, command: ChangeSalePriceCommand) {
        val product = findMine(seller, productId)
        product.changeSalePrice(command.salePrice)
        productRepository.save(product)
    }

    fun updateSku(seller: SellerPrincipal, productId: Long, skuId: Long, command: UpdateSkuCommand) {
        val product = findMine(seller, productId)
        product.updateSku(SkuId(skuId), command.extraPrice, command.stock, command.onSale)
        productRepository.save(product)
    }

    fun pause(seller: SellerPrincipal, productId: Long) = transition(seller, productId) { it.pause() }

    fun resume(seller: SellerPrincipal, productId: Long) = transition(seller, productId) { it.resume() }

    fun end(seller: SellerPrincipal, productId: Long) = transition(seller, productId) { it.end() }

    private fun transition(seller: SellerPrincipal, productId: Long, action: (Product) -> Unit) {
        val product = findMine(seller, productId)
        action(product)
        productRepository.save(product)
    }

    private fun findMine(seller: SellerPrincipal, productId: Long): Product =
        productRepository.findByIdAndSellerId(ProductId(productId), seller.sellerId)
            ?: throw DomainException(ErrorCode.PRODUCT_NOT_FOUND)
}
