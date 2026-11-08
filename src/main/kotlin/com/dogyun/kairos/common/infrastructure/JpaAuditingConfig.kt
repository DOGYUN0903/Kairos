package com.dogyun.kairos.common.infrastructure

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@Configuration
@EnableJpaAuditing(auditorAwareRef = "loginMemberAuditorAware")
class JpaAuditingConfig
