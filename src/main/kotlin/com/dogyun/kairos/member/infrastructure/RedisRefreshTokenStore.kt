package com.dogyun.kairos.member.infrastructure

import com.dogyun.kairos.member.application.RefreshTokenStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisRefreshTokenStore(private val redisTemplate: StringRedisTemplate) : RefreshTokenStore {

    override fun save(memberId: Long, token: String, ttl: Duration) {
        redisTemplate.opsForValue().set(key(memberId), token, ttl)
    }

    override fun find(memberId: Long): String? = redisTemplate.opsForValue().get(key(memberId))

    override fun delete(memberId: Long) {
        redisTemplate.delete(key(memberId))
    }

    private fun key(memberId: Long) = "refresh:$memberId"
}
