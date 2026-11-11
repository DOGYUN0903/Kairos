package com.dogyun.kairos.member.application

import com.dogyun.kairos.common.security.Role

data class MemberMeResult(val memberId: Long, val email: String, val name: String, val role: Role)
