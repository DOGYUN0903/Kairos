package com.dogyun.kairos.catalog.application

/** 카테고리에 상품이 남아 있는지 확인한다. 상품 모듈이 생기면 그 구현으로 교체한다. */
interface CategoryUsageChecker {
    fun hasProducts(categoryId: Long): Boolean
}
