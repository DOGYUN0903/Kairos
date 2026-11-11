package com.dogyun.kairos.cart.application

import com.dogyun.kairos.cart.domain.CartRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CartQueryService(
    private val cartRepository: CartRepository,
    private val productReader: ProductReader,
    private val sellerReader: SellerReader,
) {

    /** 금액은 저장하지 않고 조회 시점의 판매가로 계산한다. SKU 가 사라진 항목은 DISCONTINUED, 가격 0.
     * 판매자가 주문할 수 없는 상태면 AVAILABLE 항목을 PAUSED 로 바꾼다. */
    @Transactional(readOnly = true)
    fun get(memberId: Long): CartResult {
        val cartItems = cartRepository.findByMemberId(memberId).items
        val skus = productReader.findOrderableSkus(cartItems.map { it.skuId })
        // 판매자는 한 번에 일괄 조회한다.
        val sellers = sellerReader.findSellers(skus.values.map { it.sellerId }.distinct())
        val items = cartItems.map { item ->
            val sku = skus[item.skuId]
            val seller = sku?.let { sellers[it.sellerId] }
            // 견적·주문은 주문 가능한 판매자가 아니면 거절하므로 장바구니도 같은 기준으로 PAUSED 로 보인다.
            val availability = when {
                sku == null -> "DISCONTINUED"
                sku.availability == "AVAILABLE" && seller?.orderable != true -> "PAUSED"
                else -> sku.availability
            }
            val unitPrice = sku?.unitPrice ?: 0L
            CartItemResult(
                cartItemId = requireNotNull(item.id),
                skuId = item.skuId,
                productId = sku?.productId ?: 0L,
                sellerId = sku?.sellerId,
                sellerName = seller?.companyName,
                productName = sku?.productName.orEmpty(),
                brandName = sku?.brandName.orEmpty(),
                thumbnailUrl = sku?.thumbnailUrl.orEmpty(),
                optionDisplayName = sku?.optionDisplayName.orEmpty(),
                unitPrice = unitPrice,
                quantity = item.quantity,
                lineAmount = unitPrice * item.quantity,
                availability = availability,
            )
        }
        return CartResult(items, items.filter { it.availability == "AVAILABLE" }.sumOf { it.lineAmount })
    }
}
