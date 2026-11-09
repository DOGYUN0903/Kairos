package com.dogyun.kairos.catalog.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode

/**
 * 심사 대상이 되는 상품 내용. 가격은 포함하지 않는다.
 * imageFileIds 의 첫 번째가 대표 이미지다(스펙 14장, file_id 참조).
 */
data class ProductContent(
    val name: String,
    val description: String,
    val categoryId: Long,
    val imageFileIds: List<Long>,
    val options: List<ProductOption>,
) {
    init {
        if (name.isBlank() || name.length > MAX_NAME_LENGTH) throw DomainException(ErrorCode.PRODUCT_INVALID)
        if (imageFileIds.isEmpty() || imageFileIds.size > MAX_IMAGES) throw DomainException(ErrorCode.PRODUCT_INVALID)
        if (imageFileIds.toSet().size != imageFileIds.size) throw DomainException(ErrorCode.PRODUCT_INVALID)
        if (options.size > MAX_OPTIONS) throw DomainException(ErrorCode.PRODUCT_INVALID)
        if (options.map { it.name }.toSet().size != options.size) throw DomainException(ErrorCode.PRODUCT_INVALID)
        val optionIds = options.mapNotNull { it.id }
        if (optionIds.toSet().size != optionIds.size) throw DomainException(ErrorCode.PRODUCT_INVALID)
        val valueIds = options.flatMap { it.values }.mapNotNull { it.id }
        if (valueIds.toSet().size != valueIds.size) throw DomainException(ErrorCode.PRODUCT_INVALID)
    }

    /** 이미 저장된(id 가 있는) 옵션 값 ID 집합. */
    fun existingValueIds(): Set<Long> = options.flatMap { it.values }.mapNotNull { it.id }.toSet()

    companion object {
        const val MAX_NAME_LENGTH = 100
        const val MAX_IMAGES = 10
        const val MAX_OPTIONS = 2
    }
}
