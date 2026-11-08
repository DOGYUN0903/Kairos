package com.dogyun.kairos.member.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("kairos.admin")
data class AdminProperties(val email: String, val password: String)
