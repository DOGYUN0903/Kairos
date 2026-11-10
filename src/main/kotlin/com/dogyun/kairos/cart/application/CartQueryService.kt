package com.dogyun.kairos.cart.application

import com.dogyun.kairos.cart.domain.CartRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CartQueryService(
    private val cartRepository: CartRepository,
    private val productReader: ProductReader,
) {

    /** 금액은 저장하지 않고 조회 시점의 판매가로 계산한다. SKU 가 사라진 항목은 DISCONTINUED, 가격 0. */
    @Transactional(readOnly = true)
    fun get(memberId: Long): CartResult {
        val cartItems = cartRepository.findByMemberId(memberId).items
        val skus = productReader.findOrderableSkus(cartItems.map { it.skuId })
        val items = cartItems.map { item ->
            val sku = skus[item.skuId]
            val unitPrice = sku?.unitPrice ?: 0L
            CartItemResult(
                cartItemId = requireNotNull(item.id),
                skuId = item.skuId,
                productId = sku?.productId ?: 0L,
                productName = sku?.productName.orEmpty(),
                brandName = sku?.brandName.orEmpty(),
                thumbnailUrl = sku?.thumbnailUrl.orEmpty(),
                optionDisplayName = sku?.optionDisplayName.orEmpty(),
                unitPrice = unitPrice,
                quantity = item.quantity,
                lineAmount = unitPrice * item.quantity,
                availability = sku?.availability ?: "DISCONTINUED",
            )
        }
        return CartResult(items, items.filter { it.availability == "AVAILABLE" }.sumOf { it.lineAmount })
    }
}
