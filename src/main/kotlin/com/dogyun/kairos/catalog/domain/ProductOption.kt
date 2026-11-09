package com.dogyun.kairos.catalog.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode

/** 옵션 축(예: 색상). 값은 1개 이상이고 이름이 겹치지 않아야 한다. */
data class ProductOption(val id: Long?, val name: String, val values: List<OptionValue>) {
    init {
        if (name.isBlank()) throw DomainException(ErrorCode.PRODUCT_INVALID)
        if (values.isEmpty()) throw DomainException(ErrorCode.PRODUCT_INVALID)
        if (values.any { it.name.isBlank() }) throw DomainException(ErrorCode.PRODUCT_INVALID)
        if (values.map { it.name }.toSet().size != values.size) throw DomainException(ErrorCode.PRODUCT_INVALID)
    }
}
