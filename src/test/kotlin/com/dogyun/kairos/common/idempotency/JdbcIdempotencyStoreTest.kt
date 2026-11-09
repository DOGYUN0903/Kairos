package com.dogyun.kairos.common.idempotency

import com.dogyun.kairos.IntegrationTestSupport
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class JdbcIdempotencyStoreTest : IntegrationTestSupport() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var store: JdbcIdempotencyStore

    private val now = LocalDateTime.of(2026, 11, 10, 12, 0, 0)
    private val timeout = Duration.ofSeconds(60)

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM idempotency_key")
        store = JdbcIdempotencyStore(jdbcTemplate)
    }

    private fun begin(hash: String = "a".repeat(64), at: LocalDateTime = now, memberId: Long = 1L, key: String = "key-1") =
        store.begin(memberId, key, hash, at, at.plusHours(24), timeout)

    @Test
    fun 처음_begin은_Started다() {
        assertEquals(BeginResult.Started, begin())
    }

    @Test
    fun 처리_중_같은_키는_InProgress다() {
        begin()

        assertEquals(BeginResult.InProgress, begin(at = now.plusSeconds(10)))
    }

    @Test
    fun complete_뒤_같은_키는_Completed로_저장된_응답을_준다() {
        begin()
        store.complete(1L, "key-1", 201, """{"message":"ok"}""")

        assertEquals(BeginResult.Completed(201, """{"message":"ok"}"""), begin(at = now.plusSeconds(10)))
    }

    @Test
    fun 같은_키_다른_해시는_HashMismatch다() {
        begin()

        assertEquals(BeginResult.HashMismatch, begin(hash = "b".repeat(64), at = now.plusSeconds(10)))
    }

    @Test
    fun 만료된_키는_새_요청으로_본다() {
        begin()
        store.complete(1L, "key-1", 201, "{}")

        assertEquals(BeginResult.Started, begin(hash = "b".repeat(64), at = now.plusHours(25)))
    }

    @Test
    fun `60초 넘게 처리 중인 같은 해시는 Started로 다시 실행한다`() {
        begin()

        assertEquals(BeginResult.Started, begin(at = now.plusSeconds(61)))
        // 정체 판정 시각이 갱신되었으므로 곧바로 다시 오면 처리 중이다.
        assertEquals(BeginResult.InProgress, begin(at = now.plusSeconds(70)))
    }

    @Test
    fun release한_키는_다시_Started다() {
        begin()
        store.release(1L, "key-1")

        assertEquals(BeginResult.Started, begin(at = now.plusSeconds(1)))
    }

    @Test
    fun 다른_회원의_같은_키는_독립이다() {
        begin(memberId = 1L)

        assertEquals(BeginResult.Started, begin(memberId = 2L))
    }

    @Test
    fun deleteExpired는_만료된_행_수를_돌려준다() {
        begin(key = "k1")
        begin(key = "k2")
        begin(key = "k3", at = now.plusHours(30))

        assertEquals(2, store.deleteExpired(now.plusHours(25)))
    }
}
