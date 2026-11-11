package com.dogyun.kairos.demo.infrastructure

import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/** 운영(prod)에서 demo 프로필을 실수로 함께 켜면 기동을 막는다. */
@Component
@Profile("demo")
class DemoProfileGuard(environment: Environment) {
    init {
        check("prod" !in environment.activeProfiles) { "prod 프로필에서는 demo 프로필을 켤 수 없습니다" }
    }
}
