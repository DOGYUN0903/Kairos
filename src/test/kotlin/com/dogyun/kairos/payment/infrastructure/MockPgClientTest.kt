package com.dogyun.kairos.payment.infrastructure

import com.dogyun.kairos.payment.application.PgResponse
import java.time.Duration
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockPgClientTest {

    private fun client(delay: Long, timeout: Long, failureRate: Double = 0.0) =
        MockPgClient(MockPgProperties(Duration.ofMillis(delay), failureRate, Duration.ofMillis(timeout)), Random(42))

    @Test
    fun 실패율_0이면_항상_승인된다() {
        val client = client(delay = 1, timeout = 100, failureRate = 0.0)
        repeat(50) { assertEquals(PgResponse.APPROVED, client.approve(1L, 1000L)) }
    }

    @Test
    fun 실패율_1이면_항상_실패() {
        val client = client(delay = 1, timeout = 100, failureRate = 1.0)
        repeat(50) { assertEquals(PgResponse.FAILED, client.approve(1L, 1000L)) }
    }

    @Test
    fun 지연이_타임아웃보다_길면_TIMEOUT이고_타임아웃만큼만_기다린다() {
        val client = client(delay = 500, timeout = 30)
        val start = System.nanoTime()
        val response = client.approve(1L, 1000L)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(PgResponse.TIMEOUT, response)
        assertTrue(elapsedMs >= 30, "timeout만큼은 대기해야 한다: ${elapsedMs}ms")
        assertTrue(elapsedMs < 300, "delay(500ms)를 다 기다리면 안 된다: ${elapsedMs}ms")
    }

    @Test
    fun 호출_스레드를_delay만큼_블로킹한다() {
        val client = client(delay = 50, timeout = 1000)
        val start = System.nanoTime()
        client.approve(1L, 1000L)
        assertTrue((System.nanoTime() - start) / 1_000_000 >= 50)
    }
}
