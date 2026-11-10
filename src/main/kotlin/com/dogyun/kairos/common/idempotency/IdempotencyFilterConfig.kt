package com.dogyun.kairos.common.idempotency

import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import java.time.Clock

@Configuration
class IdempotencyFilterConfig {

    /** 필터를 빈으로 두지 않고 등록 빈으로만 만든다(서블릿 컨테이너에 중복 등록되지 않게). 보안 필터 체인 뒤에서 실행한다. */
    @Bean
    fun idempotencyFilterRegistration(
        store: IdempotencyStore,
        properties: IdempotencyProperties,
        objectMapper: ObjectMapper,
        clock: Clock,
    ): FilterRegistrationBean<IdempotencyFilter> =
        FilterRegistrationBean(IdempotencyFilter(store, properties, objectMapper, clock)).apply {
            order = SecurityFilterProperties.DEFAULT_FILTER_ORDER + 10
        }
}
