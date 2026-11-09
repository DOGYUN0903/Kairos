package com.dogyun.kairos.common.security

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import org.springframework.core.MethodParameter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.ServletWebRequest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AuthenticatedSellerArgumentResolverTest {

    @Suppress("unused", "UNUSED_PARAMETER")
    private class DummyController {
        fun manage(@AuthenticatedSeller seller: SellerPrincipal) {}
        fun view(@AuthenticatedSeller(requireManage = false) seller: SellerPrincipal) {}
        fun plain(seller: SellerPrincipal) {}
    }

    private class FakeSellerResolver(private val principal: SellerPrincipal?) : SellerResolver {
        override fun resolve(memberId: Long): SellerPrincipal? = principal
    }

    private val request = ServletWebRequest(MockHttpServletRequest())

    private fun parameter(name: String): MethodParameter =
        MethodParameter(DummyController::class.java.methods.first { it.name == name }, 0)

    private fun login(memberId: Long = 7L) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(LoginMember(memberId, Role.USER), null, emptyList())
    }

    private fun resolve(principal: SellerPrincipal?, method: String) =
        AuthenticatedSellerArgumentResolver(FakeSellerResolver(principal))
            .resolveArgument(parameter(method), null, request, null)

    @AfterTest
    fun clearContext() = SecurityContextHolder.clearContext()

    @Test
    fun 어노테이션이_붙은_SellerPrincipal_파라미터만_지원한다() {
        val resolver = AuthenticatedSellerArgumentResolver(FakeSellerResolver(null))

        assertTrue(resolver.supportsParameter(parameter("manage")))
        assertEquals(false, resolver.supportsParameter(parameter("plain")))
    }

    @Test
    fun 인증이_없으면_UNAUTHORIZED() {
        val e = assertFailsWith<DomainException> { resolve(null, "manage") }

        assertEquals(ErrorCode.UNAUTHORIZED, e.errorCode)
    }

    @Test
    fun 판매자가_없으면_FORBIDDEN() {
        login()

        val e = assertFailsWith<DomainException> { resolve(null, "manage") }

        assertEquals(ErrorCode.FORBIDDEN, e.errorCode)
    }

    @Test
    fun requireManage인데_canManage가_아니면_SELLER_NOT_ACTIVE() {
        login()

        val e = assertFailsWith<DomainException> { resolve(SellerPrincipal(1, 7, canManage = false, canView = true), "manage") }

        assertEquals(ErrorCode.SELLER_NOT_ACTIVE, e.errorCode)
    }

    @Test
    fun requireManage_false면_canView만_있으면_통과() {
        login()
        val suspended = SellerPrincipal(1, 7, canManage = false, canView = true)

        assertSame(suspended, resolve(suspended, "view"))
    }

    @Test
    fun requireManage_false여도_canView가_아니면_SELLER_NOT_ACTIVE() {
        login()

        val e = assertFailsWith<DomainException> { resolve(SellerPrincipal(1, 7, canManage = false, canView = false), "view") }

        assertEquals(ErrorCode.SELLER_NOT_ACTIVE, e.errorCode)
    }

    @Test
    fun 관리_가능한_판매자는_통과한다() {
        login()
        val approved = SellerPrincipal(1, 7, canManage = true, canView = true)

        assertSame(approved, resolve(approved, "manage"))
    }
}
