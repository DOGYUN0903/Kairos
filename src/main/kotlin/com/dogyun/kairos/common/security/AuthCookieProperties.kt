package com.dogyun.kairos.common.security

import org.springframework.boot.context.properties.ConfigurationProperties

/** 리프레시 토큰 쿠키 설정. 운영(HTTPS)에서는 cookieSecure 를 켠다. */
@ConfigurationProperties("kairos.auth")
data class AuthCookieProperties(val cookieSecure: Boolean = false)
