package com.dogyun.kairos.member.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.security.Role
import com.dogyun.kairos.member.domain.Email
import com.dogyun.kairos.member.domain.Member
import com.dogyun.kairos.member.domain.MemberId
import com.dogyun.kairos.member.domain.MemberRepository
import com.dogyun.kairos.member.domain.PasswordHasher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SignUpServiceTest {

    private class FakePasswordHasher : PasswordHasher {
        override fun hash(rawPassword: String) = "hashed:$rawPassword"
        override fun matches(rawPassword: String, hashed: String) = hashed == "hashed:$rawPassword"
    }

    private class FakeMemberRepository : MemberRepository {
        val members = mutableMapOf<Long, Member>()
        private var seq = 0L

        override fun save(member: Member): Member {
            val saved = Member.restore(MemberId(++seq), member.email, member.passwordHash, member.name, member.role)
            members[seq] = saved
            return saved
        }

        override fun findById(id: MemberId): Member? = members[id.value]
        override fun findByEmail(email: Email): Member? = members.values.firstOrNull { it.email == email }
        override fun existsByEmail(email: Email): Boolean = findByEmail(email) != null
        override fun findAllByIds(ids: Collection<MemberId>): List<Member> = ids.mapNotNull { members[it.value] }
    }

    private val members = FakeMemberRepository()
    private val service = SignUpService(members, FakePasswordHasher())

    @Test
    fun signUpAdmin은_ADMIN_역할로_가입한다() {
        val id = service.signUpAdmin(SignUpCommand("admin@kairos.demo", "password1", "데모 관리자"))

        assertEquals(Role.ADMIN, members.members.getValue(id).role)
    }

    @Test
    fun signUp은_USER_역할로_가입한다() {
        val id = service.signUp(SignUpCommand("user@kairos.demo", "password1", "구매자"))

        assertEquals(Role.USER, members.members.getValue(id).role)
    }

    @Test
    fun signUpAdmin도_중복_이메일은_거절한다() {
        service.signUp(SignUpCommand("dup@kairos.demo", "password1", "구매자"))

        val e = assertFailsWith<DomainException> { service.signUpAdmin(SignUpCommand("dup@kairos.demo", "password1", "관리자")) }
        assertEquals(ErrorCode.MEMBER_EMAIL_DUPLICATED, e.errorCode)
    }
}
