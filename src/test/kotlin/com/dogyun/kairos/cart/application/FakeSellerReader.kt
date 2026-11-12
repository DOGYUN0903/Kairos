package com.dogyun.kairos.cart.application

class FakeSellerReader : SellerReader {
    val sellers = mutableMapOf<Long, CartSellerInfo>()

    fun put(sellerId: Long, companyName: String = "키로스상사", orderable: Boolean = true) {
        sellers[sellerId] = CartSellerInfo(sellerId, companyName, orderable)
    }

    override fun findSellers(sellerIds: Collection<Long>): Map<Long, CartSellerInfo> =
        sellerIds.mapNotNull { sellers[it] }.associateBy { it.sellerId }
}
