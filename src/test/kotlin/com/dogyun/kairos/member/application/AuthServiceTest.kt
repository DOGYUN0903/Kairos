package com.dogyun.kairos.member.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.security.JwtProperties
import com.dogyun.kairos.common.security.JwtProvider
import com.dogyun.kairos.common.security.Role
import com.dogyun.kairos.member.domain.Email
import com.dogyun.kairos.member.domain.Member
import com.dogyun.kairos.member.domain.MemberId
import com.dogyun.kairos.member.domain.MemberRepository
import com.dogyun.kairos.member.domain.PasswordHasher
import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AuthServiceTest {

    private class FakePasswordHasher : PasswordHasher {
        override fun hash(rawPassword: String) = "hashed:$rawPassword"
        override fun matches(rawPassword: String, hashed: String) = hashed == "hashed:$rawPassword"
    }

    private class FakeMemberRepository : MemberRepository {
        private val members = mutableMapOf<Long, Member>()
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

    private class FakeRefreshTokenStore : RefreshTokenStore {
        val tokens = mutableMapOf<Long, String>()
        override fun save(memberId: Long, token: String, ttl: Duration) { tokens[memberId] = token }
        override fun find(memberId: Long): String? = tokens[memberId]
        override fun delete(memberId: Long) { tokens.remove(memberId) }
    }

    private val hasher = FakePasswordHasher()
    private val members = FakeMemberRepository()
    private val store = FakeRefreshTokenStore()
    private val jwt = JwtProvider(
        JwtProperties("unit-test-secret-key-0123456789-abcdefghij", Duration.ofMinutes(30), Duration.ofDays(14)),
    )
    private val signUpService = SignUpService(members, hasher)
    private val authService = AuthService(members, hasher, jwt, store)

    private var memberId = 0L

    @BeforeTest
    fun setUp() {
        memberId = signUpService.signUp(SignUpCommand("user@kairos.com", "password1", "홍길동"))
    }

    @Test
    fun 중복_이메일_가입은_거절된다() {
        val e = assertFailsWith<DomainException> {
            signUpService.signUp(SignUpCommand("USER@kairos.com", "password1", "다른사람"))
        }
        assertEquals(ErrorCode.MEMBER_EMAIL_DUPLICATED, e.errorCode)
    }

    @Test
    fun 로그인하면_리프레시_토큰이_저장된다() {
        val token = authService.login(LoginCommand("user@kairos.com", "password1"))

        assertEquals(token.refreshToken, store.find(memberId))
        assertEquals(memberId, jwt.parseAccessToken(token.accessToken)?.memberId)
    }

    @Test
    fun 틀린_비밀번호와_없는_이메일은_같은_에러다() {
        val wrongPassword = assertFailsWith<DomainException> {
            authService.login(LoginCommand("user@kairos.com", "wrong-password"))
        }
        val unknownEmail = assertFailsWith<DomainException> {
            authService.login(LoginCommand("none@kairos.com", "password1"))
        }
        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, wrongPassword.errorCode)
        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, unknownEmail.errorCode)
    }

    @Test
    fun 재발급하면_리프레시_토큰이_회전하고_이전_토큰은_거절된다() {
        val first = authService.login(LoginCommand("user@kairos.com", "password1"))

        val second = authService.reissue(first.refreshToken)

        assertNotEquals(first.refreshToken, second.refreshToken)
        assertEquals(second.refreshToken, store.find(memberId))
        val e = assertFailsWith<DomainException> { authService.reissue(first.refreshToken) }
        assertEquals(ErrorCode.AUTH_INVALID_TOKEN, e.errorCode)
    }

    @Test
    fun 액세스_토큰이나_깨진_토큰으로는_재발급할_수_없다() {
        val token = authService.login(LoginCommand("user@kairos.com", "password1"))

        listOf(token.accessToken, "garbage").forEach {
            val e = assertFailsWith<DomainException> { authService.reissue(it) }
            assertEquals(ErrorCode.AUTH_INVALID_TOKEN, e.errorCode)
        }
    }

    @Test
    fun 로그아웃하면_리프레시_토큰이_삭제되고_재발급은_거절된다() {
        val token = authService.login(LoginCommand("user@kairos.com", "password1"))

        authService.logout(memberId)

        assertNull(store.find(memberId))
        assertFailsWith<DomainException> { authService.reissue(token.refreshToken) }
    }

    @Test
    fun 리프레시_토큰은_액세스_토큰으로_파싱되지_않는다() {
        val token = authService.login(LoginCommand("user@kairos.com", "password1"))

        assertNull(jwt.parseAccessToken(token.refreshToken))
        assertNotNull(jwt.parseRefreshToken(token.refreshToken))
        assertEquals(Role.USER, jwt.parseAccessToken(token.accessToken)?.role)
    }

    @Test
    fun 저장된_리프레시_토큰으로_로그아웃하면_삭제된다() {
        val token = authService.login(LoginCommand("user@kairos.com", "password1"))

        authService.logoutByRefreshToken(token.refreshToken)

        assertNull(store.find(memberId))
    }

    @Test
    fun 회전된_옛_리프레시_토큰이나_잘못된_토큰으로_로그아웃하면_무시한다() {
        val old = authService.login(LoginCommand("user@kairos.com", "password1"))
        val current = authService.reissue(old.refreshToken)

        listOf(old.refreshToken, current.accessToken, "garbage").forEach { authService.logoutByRefreshToken(it) }

        assertEquals(current.refreshToken, store.find(memberId))
    }
}
