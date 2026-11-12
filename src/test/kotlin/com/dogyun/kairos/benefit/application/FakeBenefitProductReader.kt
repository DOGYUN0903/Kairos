package com.dogyun.kairos.benefit.application

class FakeBenefitProductReader : BenefitProductReader {
    val products = mutableMapOf<Long, ProductRef>()
    val names = mutableMapOf<Long, String>()
    var findProductNamesCalls = 0

    fun put(productId: Long, sellerId: Long) {
        products[productId] = ProductRef(productId, sellerId)
    }

    fun putName(productId: Long, name: String) {
        names[productId] = name
    }

    override fun findProductNames(productIds: Collection<Long>): Map<Long, String> {
        findProductNamesCalls++
        return names.filterKeys { it in productIds }
    }

    override fun findProduct(productId: Long): ProductRef? = products[productId]
}
