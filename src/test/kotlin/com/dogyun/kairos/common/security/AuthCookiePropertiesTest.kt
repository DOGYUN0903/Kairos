package com.dogyun.kairos.common.security

import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthCookiePropertiesTest {

    private fun bind(vararg files: String): AuthCookieProperties {
        val env = StandardEnvironment()
        // 뒤 파일이 앞 파일을 덮어쓰도록(프로필 yaml 우선) 앞에 추가한다.
        files.forEach { file ->
            YamlPropertySourceLoader().load(file, ClassPathResource(file)).forEach { env.propertySources.addFirst(it) }
        }
        return Binder.get(env).bindOrCreate("kairos.auth", AuthCookieProperties::class.java)
    }

    @Test
    fun 기본_설정은_쿠키_Secure를_끈다() {
        assertFalse(bind("application.yaml").cookieSecure)
    }

    @Test
    fun 운영_프로필은_쿠키_Secure를_켠다() {
        assertTrue(bind("application.yaml", "application-prod.yaml").cookieSecure)
    }
}
