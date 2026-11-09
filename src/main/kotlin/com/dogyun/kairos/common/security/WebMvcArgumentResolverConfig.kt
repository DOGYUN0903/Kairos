package com.dogyun.kairos.common.security

import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcArgumentResolverConfig(
    private val authenticatedSellerArgumentResolver: AuthenticatedSellerArgumentResolver,
) : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(authenticatedSellerArgumentResolver)
    }
}
