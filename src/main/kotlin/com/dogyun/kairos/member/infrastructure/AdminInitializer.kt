package com.dogyun.kairos.member.infrastructure

import com.dogyun.kairos.common.security.Role
import com.dogyun.kairos.member.domain.Email
import com.dogyun.kairos.member.domain.Member
import com.dogyun.kairos.member.domain.MemberRepository
import com.dogyun.kairos.member.domain.PasswordHasher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

/** 시작 시 설정된 관리자 계정이 없으면 ADMIN 으로 생성한다. 비밀번호는 로그에 남기지 않는다. */
@Component
class AdminInitializer(
    private val properties: AdminProperties,
    private val memberRepository: MemberRepository,
    private val passwordHasher: PasswordHasher,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val email = Email(properties.email)
        if (memberRepository.existsByEmail(email)) return
        memberRepository.save(Member.register(email, properties.password, ADMIN_NAME, Role.ADMIN, passwordHasher))
        log.info { "관리자 계정을 생성했습니다: ${email.value}" }
    }

    private companion object {
        const val ADMIN_NAME = "관리자"
    }
}
