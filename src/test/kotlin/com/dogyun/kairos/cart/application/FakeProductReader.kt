package com.dogyun.kairos.cart.application

class FakeProductReader : ProductReader {
    val skus = mutableMapOf<Long, CartSkuInfo>()

    fun put(skuId: Long, unitPrice: Long = 9000, availability: String = "AVAILABLE") {
        skus[skuId] = CartSkuInfo(skuId, 100L + skuId, "티셔츠", "브랜드", "http://img/$skuId.png", "블랙 / M", unitPrice, availability)
    }

    override fun findOrderableSkus(skuIds: Collection<Long>): Map<Long, CartSkuInfo> =
        skuIds.mapNotNull { skus[it] }.associateBy { it.skuId }
}
