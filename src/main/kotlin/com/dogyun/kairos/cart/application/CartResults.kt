package com.dogyun.kairos.cart.application

data class CartItemResult(
    val cartItemId: Long,
    val skuId: Long,
    val productId: Long,
    val productName: String,
    val brandName: String,
    val thumbnailUrl: String,
    val optionDisplayName: String,
    val unitPrice: Long,
    val quantity: Int,
    val lineAmount: Long,
    val availability: String,
)

data class CartResult(
    val items: List<CartItemResult>,
    /** AVAILABLE 항목의 lineAmount 합 */
    val availableAmount: Long,
)
