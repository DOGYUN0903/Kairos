package com.dogyun.kairos.common.security

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class AuthenticatedSellerArgumentResolver(private val sellerResolver: SellerResolver) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(AuthenticatedSeller::class.java) &&
            parameter.parameterType == SellerPrincipal::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        val loginMember = SecurityContextHolder.getContext().authentication?.principal as? LoginMember
            ?: throw DomainException(ErrorCode.UNAUTHORIZED)
        val seller = sellerResolver.resolve(loginMember.id) ?: throw DomainException(ErrorCode.FORBIDDEN)
        val requireManage = parameter.getParameterAnnotation(AuthenticatedSeller::class.java)?.requireManage ?: true
        val allowed = if (requireManage) seller.canManage else seller.canView
        if (!allowed) throw DomainException(ErrorCode.SELLER_NOT_ACTIVE)
        return seller
    }
}
