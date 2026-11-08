package com.dogyun.kairos.member.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.security.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MemberTest {

    private val hasher = object : PasswordHasher {
        override fun hash(rawPassword: String) = "hashed:$rawPassword"
        override fun matches(rawPassword: String, hashed: String) = hashed == "hashed:$rawPassword"
    }

    @Test
    fun 잘못된_이메일_형식은_거절된다() {
        val e = assertFailsWith<DomainException> { Email("not-an-email") }
        assertEquals(ErrorCode.INVALID_REQUEST, e.errorCode)
    }

    @Test
    fun 올바른_이메일은_소문자로_정규화된다() {
        assertEquals("a@b.com", Email("A@B.com").value)
    }

    @Test
    fun 가입하면_비밀번호는_해시로_저장되고_검증된다() {
        val member = Member.register(Email("a@b.com"), "password1", "홍길동", Role.USER, hasher)

        assertNotEquals("password1", member.passwordHash)
        assertTrue(member.matchesPassword("password1", hasher))
        assertFalse(member.matchesPassword("wrong-pass", hasher))
        assertEquals(Role.USER, member.role)
    }

    @Test
    fun 짧은_비밀번호는_거절된다() {
        val e = assertFailsWith<DomainException> {
            Member.register(Email("a@b.com"), "short", "홍길동", Role.USER, hasher)
        }
        assertEquals(ErrorCode.INVALID_REQUEST, e.errorCode)
    }

    @Test
    fun 빈_이름은_거절된다() {
        assertFailsWith<DomainException> {
            Member.register(Email("a@b.com"), "password1", " ", Role.USER, hasher)
        }
    }
}
