package com.dogyun.kairos.common.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.Date
import java.util.UUID

@Component
class JwtProvider(private val properties: JwtProperties) {

    /** secret 이 짧으면 여기서 즉시 실패한다(WeakKeyException). */
    private val key = Keys.hmacShaKeyFor(properties.secret.toByteArray(Charsets.UTF_8))

    val refreshTtl: Duration get() = properties.refreshTtl

    fun createAccessToken(memberId: Long, role: Role): String =
        build(memberId, TYPE_ACCESS, properties.accessTtl, mapOf(CLAIM_ROLE to role.name))

    /** jti 를 넣어 같은 초에 발급해도 토큰이 매번 달라지게 한다(회전 시 옛 토큰과 구분). */
    fun createRefreshToken(memberId: Long): String =
        build(memberId, TYPE_REFRESH, properties.refreshTtl, mapOf("jti" to UUID.randomUUID().toString()))

    fun parseAccessToken(token: String): AccessClaims? {
        val claims = parse(token, TYPE_ACCESS) ?: return null
        val role = runCatching { Role.valueOf(claims.get(CLAIM_ROLE, String::class.java)) }.getOrNull() ?: return null
        val memberId = claims.subject.toLongOrNull() ?: return null
        return AccessClaims(memberId, role)
    }

    fun parseRefreshToken(token: String): RefreshClaims? {
        val claims = parse(token, TYPE_REFRESH) ?: return null
        return RefreshClaims(claims.subject.toLongOrNull() ?: return null)
    }

    private fun build(memberId: Long, type: String, ttl: Duration, extra: Map<String, Any>): String {
        val now = System.currentTimeMillis()
        return Jwts.builder()
            .subject(memberId.toString())
            .claim(CLAIM_TYPE, type)
            .claims(extra)
            .issuedAt(Date(now))
            .expiration(Date(now + ttl.toMillis()))
            .signWith(key)
            .compact()
    }

    private fun parse(token: String, expectedType: String): Claims? =
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
                .takeIf { it.get(CLAIM_TYPE, String::class.java) == expectedType }
        } catch (_: JwtException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    data class AccessClaims(val memberId: Long, val role: Role)
    data class RefreshClaims(val memberId: Long)

    private companion object {
        const val CLAIM_TYPE = "type"
        const val CLAIM_ROLE = "role"
        const val TYPE_ACCESS = "access"
        const val TYPE_REFRESH = "refresh"
    }
}
