package com.dogyun.kairos.common.infrastructure

import com.dogyun.kairos.common.security.LoginMember
import org.springframework.data.domain.AuditorAware
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.Optional

/** 인증된 회원 id 를 감사 주체로 사용하고, 인증 정보가 없으면 0 을 사용한다. */
@Component
class LoginMemberAuditorAware : AuditorAware<Long> {
    override fun getCurrentAuditor(): Optional<Long> {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        return Optional.of((principal as? LoginMember)?.id ?: 0L)
    }
}
