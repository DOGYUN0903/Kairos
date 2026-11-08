package com.dogyun.kairos.member.domain

interface PasswordHasher {
    fun hash(rawPassword: String): String
    fun matches(rawPassword: String, hashed: String): Boolean
}
