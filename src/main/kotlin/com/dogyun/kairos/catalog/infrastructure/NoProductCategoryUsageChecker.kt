package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.catalog.application.CategoryUsageChecker
import org.springframework.stereotype.Component

/** 상품 모듈이 생기기 전까지의 임시 구현. 상품 Task 가 실제 조회로 교체한다. */
@Component
class NoProductCategoryUsageChecker : CategoryUsageChecker {
    override fun hasProducts(categoryId: Long): Boolean = false
}
