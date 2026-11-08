package com.dogyun.kairos.member.application

import java.time.Duration

/** 회원당 유효한 리프레시 토큰 하나만 보관한다. */
interface RefreshTokenStore {
    fun save(memberId: Long, token: String, ttl: Duration)
    fun find(memberId: Long): String?
    fun delete(memberId: Long)
}
