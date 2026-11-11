package com.dogyun.kairos.demo.application

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Profile

/**
 * 데모 계정 공통 비밀번호. demo 프로필에서만 빈으로 등록된다.
 * `@ConfigurationPropertiesScan` 이 패키지 기준으로 등록하므로 `@Profile` 이 없으면 다른 프로필의 기동이 바인딩 실패로 깨진다.
 */
@Profile("demo")
@ConfigurationProperties("kairos.demo")
data class DemoProperties(val password: String) {
    init {
        require(password.length >= 8) { "kairos.demo.password(KAIROS_DEMO_PASSWORD)는 8자 이상이어야 합니다" }
    }

    override fun toString(): String = "DemoProperties(password=****)"
}
