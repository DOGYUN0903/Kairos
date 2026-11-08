package com.dogyun.kairos.member.infrastructure

import com.dogyun.kairos.member.domain.PasswordHasher
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

@Component
class BCryptPasswordHasher : PasswordHasher {
    private val encoder = BCryptPasswordEncoder()

    override fun hash(rawPassword: String): String = encoder.encode(rawPassword)!!

    override fun matches(rawPassword: String, hashed: String): Boolean = rawPassword.toByteArray().size <= 72 && encoder.matches(rawPassword, hashed)
}
