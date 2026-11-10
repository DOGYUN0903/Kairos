package com.dogyun.kairos.common.idempotency

import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Duration
import java.time.LocalDateTime

/** `idempotency_key` 테이블을 JPA 없이 다룬다. 호출자의 트랜잭션과 무관하게 즉시 커밋된다. */
@Repository
class JdbcIdempotencyStore(private val jdbcTemplate: JdbcTemplate) : IdempotencyStore {

    private data class Row(
        val id: Long,
        val requestHash: String,
        val state: String,
        val responseStatus: Int?,
        val responseBody: String?,
        val expiresAt: LocalDateTime,
        val modifiedAt: LocalDateTime,
    )

    override fun begin(
        memberId: Long,
        key: String,
        requestHash: String,
        now: LocalDateTime,
        expiresAt: LocalDateTime,
        inProgressTimeout: Duration,
    ): BeginResult {
        repeat(MAX_ATTEMPTS) {
            if (tryInsert(memberId, key, requestHash, now, expiresAt)) return BeginResult.Started
            // 충돌: 기존 행을 읽는다. 그 사이 지워졌다면 다시 INSERT 를 시도한다.
            val row = find(memberId, key) ?: return@repeat
            if (!row.expiresAt.isAfter(now)) {
                jdbcTemplate.update("DELETE FROM idempotency_key WHERE id = ? AND expires_at <= ?", row.id, now)
                return@repeat
            }
            if (row.requestHash != requestHash) return BeginResult.HashMismatch
            if (row.state == COMPLETED) return BeginResult.Completed(row.responseStatus ?: 0, row.responseBody ?: "")
            if (row.modifiedAt.plus(inProgressTimeout).isBefore(now)) {
                // 정체된 처리 중 행: 한 요청만 이어받도록 modified_at 이 그대로일 때만 갱신한다.
                val taken = jdbcTemplate.update(
                    "UPDATE idempotency_key SET modified_at = ? WHERE id = ? AND state = ? AND modified_at = ?",
                    now, row.id, PROCESSING, row.modifiedAt,
                )
                return if (taken == 1) BeginResult.Started else BeginResult.InProgress
            }
            return BeginResult.InProgress
        }
        return BeginResult.InProgress
    }

    override fun complete(memberId: Long, key: String, status: Int, body: String) {
        jdbcTemplate.update(
            "UPDATE idempotency_key SET state = ?, response_status = ?, response_body = ?, modified_at = ? " +
                "WHERE member_id = ? AND idem_key = ?",
            COMPLETED, status, body, LocalDateTime.now(), memberId, key,
        )
    }

    override fun release(memberId: Long, key: String) {
        jdbcTemplate.update(
            "DELETE FROM idempotency_key WHERE member_id = ? AND idem_key = ? AND state = ?",
            memberId, key, PROCESSING,
        )
    }

    override fun deleteExpired(now: LocalDateTime): Int =
        jdbcTemplate.update("DELETE FROM idempotency_key WHERE expires_at <= ?", now)

    private fun tryInsert(
        memberId: Long, key: String, requestHash: String, now: LocalDateTime, expiresAt: LocalDateTime,
    ): Boolean = try {
        jdbcTemplate.update(
            "INSERT INTO idempotency_key (member_id, idem_key, request_hash, state, expires_at, created_at, modified_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
            memberId, key, requestHash, PROCESSING, expiresAt, now, now,
        )
        true
    } catch (e: DuplicateKeyException) {
        false
    }

    private fun find(memberId: Long, key: String): Row? = jdbcTemplate.query(
        "SELECT id, request_hash, state, response_status, response_body, expires_at, modified_at " +
            "FROM idempotency_key WHERE member_id = ? AND idem_key = ?",
        { rs: ResultSet, _: Int ->
            Row(
                rs.getLong("id"),
                rs.getString("request_hash"),
                rs.getString("state"),
                rs.getInt("response_status").takeUnless { rs.wasNull() },
                rs.getString("response_body"),
                rs.getObject("expires_at", LocalDateTime::class.java),
                rs.getObject("modified_at", LocalDateTime::class.java),
            )
        },
        memberId, key,
    ).firstOrNull()

    private companion object {
        const val PROCESSING = "PROCESSING"
        const val COMPLETED = "COMPLETED"
        const val MAX_ATTEMPTS = 3
    }
}
