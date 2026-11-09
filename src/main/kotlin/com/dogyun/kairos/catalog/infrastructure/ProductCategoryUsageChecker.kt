package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.catalog.application.CategoryUsageChecker
import com.dogyun.kairos.catalog.domain.ProductRepository
import org.springframework.stereotype.Component

/** 소프트 딜리트되지 않은 상품이 하나라도 그 카테고리를 쓰면 삭제할 수 없다. */
@Component
class ProductCategoryUsageChecker(private val productRepository: ProductRepository) : CategoryUsageChecker {
    override fun hasProducts(categoryId: Long): Boolean = productRepository.existsByCategoryId(categoryId)
}
