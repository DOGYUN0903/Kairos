package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.OptionValue
import com.dogyun.kairos.catalog.domain.Product
import com.dogyun.kairos.catalog.domain.ProductContent
import com.dogyun.kairos.catalog.domain.ProductId
import com.dogyun.kairos.catalog.domain.ProductOption
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
        val content = validatedContent(seller, command.content)
        val product = Product.register(seller.sellerId, brand.brandId, content, command.originalPrice, command.salePrice)
        return requireNotNull(productRepository.save(product).id).value
    }

    fun editContent(seller: SellerPrincipal, productId: Long, command: ProductContentCommand) {
        val product = findMine(seller, productId)
        val content = validatedContent(seller, command)
        requireKnownOptionIds(product, content)
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

    /** 카테고리는 소분류, 이미지는 전부 존재하는 본인 업로드 이미지여야 한다. */
    private fun validatedContent(seller: SellerPrincipal, command: ProductContentCommand): ProductContent {
        val content = ProductContent(
            name = command.name,
            description = command.description,
            categoryId = command.categoryId,
            imageFileIds = command.imageFileIds,
            options = command.options.map { option ->
                ProductOption(option.optionId, option.name, option.values.map { OptionValue(it.valueId, it.name) })
            },
        )
        categoryQueryService.requireLeaf(content.categoryId)
        val files = imageFileReader.findImages(content.imageFileIds)
        val owned = content.imageFileIds.all { id ->
            files[id]?.let { it.isImage && it.uploaderMemberId == seller.memberId } ?: false
        }
        if (!owned) throw DomainException(ErrorCode.FILE_NOT_OWNED)
        return content
    }

    /** 유지하겠다고 보낸 옵션 ID(값 ID 는 도메인이 검사한다)는 이 상품의 기존 옵션이어야 한다. */
    private fun requireKnownOptionIds(product: Product, content: ProductContent) {
        val existing = product.content.options.mapNotNull { it.id }.toSet()
        if (!existing.containsAll(content.options.mapNotNull { it.id })) throw DomainException(ErrorCode.PRODUCT_INVALID)
    }
}
