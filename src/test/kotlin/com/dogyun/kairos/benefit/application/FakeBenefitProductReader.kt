package com.dogyun.kairos.benefit.application

class FakeBenefitProductReader : BenefitProductReader {
    val products = mutableMapOf<Long, ProductRef>()

    fun put(productId: Long, sellerId: Long) {
        products[productId] = ProductRef(productId, sellerId)
    }

    override fun findProduct(productId: Long): ProductRef? = products[productId]
}
