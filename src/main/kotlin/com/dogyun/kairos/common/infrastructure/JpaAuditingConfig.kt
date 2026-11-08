package com.dogyun.kairos.common.infrastructure

import com.dogyun.kairos.common.security.LoginMember
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
class JpaAuditingConfig {

    @Bean
    fun auditorAware(): AuditorAware<Long> = AuditorAware {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        Optional.of((principal as? LoginMember)?.id ?: 0L)
    }
}
