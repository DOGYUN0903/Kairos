package com.dogyun.kairos.common

import com.dogyun.kairos.common.infrastructure.LoginMemberAuditorAware
import com.dogyun.kairos.common.security.LoginMember
import com.dogyun.kairos.common.security.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder

class LoginMemberAuditorAwareTest {

    private val auditor = LoginMemberAuditorAware()

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    @Test
    fun `LoginMember principal 이면 회원 id 를 반환한다`() {
        val auth = UsernamePasswordAuthenticationToken(LoginMember(7L, Role.values().first()), null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
        assertThat(auditor.currentAuditor.get()).isEqualTo(7L)
    }

    @Test
    fun `인증 정보가 없으면 0 을 반환한다`() {
        SecurityContextHolder.clearContext()
        assertThat(auditor.currentAuditor.get()).isEqualTo(0L)
    }

    @Test
    fun `익명 인증이면 0 을 반환한다`() {
        SecurityContextHolder.getContext().authentication =
            AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))
        assertThat(auditor.currentAuditor.get()).isEqualTo(0L)
    }
}
