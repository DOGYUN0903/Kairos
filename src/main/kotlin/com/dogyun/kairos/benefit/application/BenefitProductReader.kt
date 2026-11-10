package com.dogyun.kairos.benefit.application

/** benefit 이 상품의 존재와 판매자를 확인하는 포트. 구현은 infrastructure 가 catalog 서비스를 호출해 채운다. */
interface BenefitProductReader {
    /** 삭제된 상품은 돌려주지 않는다. */
    fun findProduct(productId: Long): ProductRef?
}

data class ProductRef(val productId: Long, val sellerId: Long)
