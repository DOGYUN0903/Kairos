package com.dogyun.kairos.seller.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode

/** 사업자등록번호. `123-45-67890` 형식만 허용한다. */
@JvmInline
value class BusinessNumber(val value: String) {
    init {
        if (!FORMAT.matches(value)) throw DomainException(ErrorCode.SELLER_INVALID)
    }

    private companion object {
        val FORMAT = Regex("^\\d{3}-\\d{2}-\\d{5}$")
    }
}
