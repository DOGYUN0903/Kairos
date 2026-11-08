package com.dogyun.kairos.member.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode

/** 형식이 검증된 이메일. 대소문자 구분 없이 같은 주소로 다루기 위해 소문자로 정규화한다. */
@JvmInline
value class Email private constructor(val value: String) {
    companion object {
        private val PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$")
        private const val MAX_LENGTH = 255

        operator fun invoke(raw: String): Email {
            val trimmed = raw.trim()
            if (trimmed.length > MAX_LENGTH || !PATTERN.matches(trimmed)) {
                throw DomainException(ErrorCode.INVALID_REQUEST)
            }
            return Email(trimmed.lowercase())
        }
    }
}
