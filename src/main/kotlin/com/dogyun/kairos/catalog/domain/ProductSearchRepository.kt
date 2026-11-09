package com.dogyun.kairos.catalog.domain

import com.dogyun.kairos.common.domain.PageResult

/** 사용자에게 노출되는 상품 조회. 구현(ADR 0002)은 brand·seller 테이블을 읽기 전용으로 조인한다. */
interface ProductSearchRepository {
    fun search(condition: ProductSearchCondition, page: Int, size: Int): PageResult<ProductListRow>

    /** ON_SALE 이고 브랜드·판매자가 모두 APPROVED 인 상품. 아니면 null. */
    fun findExposedDetail(productId: ProductId): Product?
}
