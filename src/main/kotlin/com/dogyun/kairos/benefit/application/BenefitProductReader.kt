package com.dogyun.kairos.benefit.application

/** benefit 이 상품의 존재와 판매자를 확인하는 포트. 구현은 infrastructure 가 catalog 서비스를 호출해 채운다. */
interface BenefitProductReader {
    /** 삭제된 상품은 돌려주지 않는다. */
    fun findProduct(productId: Long): ProductRef?

    /** 이름을 한 번에 조회한다. 없는·삭제된 상품은 결과에서 빠진다. */
    fun findProductNames(productIds: Collection<Long>): Map<Long, String>
}

/** 정책 한 페이지의 대상 상품 이름을 한 번에 조회한다(대상 상품이 없으면 조회하지 않는다). */
internal fun BenefitProductReader.policyProductNames(policies: List<com.dogyun.kairos.benefit.domain.CouponPolicy>): Map<Long, String> {
    val ids = policies.mapNotNull { it.targetProductId }.toSet()
    return if (ids.isEmpty()) emptyMap() else findProductNames(ids)
}

data class ProductRef(val productId: Long, val sellerId: Long)
