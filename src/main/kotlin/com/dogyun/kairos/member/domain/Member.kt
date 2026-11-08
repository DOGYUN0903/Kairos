package com.dogyun.kairos.member.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.security.Role

class Member private constructor(
    val id: MemberId?,
    val email: Email,
    val passwordHash: String,
    val name: String,
    val role: Role,
) {
    fun matchesPassword(rawPassword: String, hasher: PasswordHasher): Boolean =
        hasher.matches(rawPassword, passwordHash)

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_NAME_LENGTH = 50
        const val MAX_PASSWORD_BYTES = 72 // BCrypt 입력 한계

        fun register(email: Email, rawPassword: String, name: String, role: Role, hasher: PasswordHasher): Member {
            if (rawPassword.length < MIN_PASSWORD_LENGTH || rawPassword.toByteArray().size > MAX_PASSWORD_BYTES) throw DomainException(ErrorCode.INVALID_REQUEST)
            val trimmedName = name.trim()
            if (trimmedName.isEmpty() || trimmedName.length > MAX_NAME_LENGTH) {
                throw DomainException(ErrorCode.INVALID_REQUEST)
            }
            return Member(null, email, hasher.hash(rawPassword), trimmedName, role)
        }

        /** 저장소에서 읽은 값으로 복원한다. 검증과 해싱은 하지 않는다. */
        fun restore(id: MemberId, email: Email, passwordHash: String, name: String, role: Role): Member =
            Member(id, email, passwordHash, name, role)
    }
}
