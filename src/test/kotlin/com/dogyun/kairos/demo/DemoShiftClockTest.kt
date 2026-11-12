package com.dogyun.kairos.demo

import com.dogyun.kairos.demo.application.DemoShiftClock
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DemoShiftClockTest {

    private val clock = DemoShiftClock()
    private val week = Duration.ofDays(-7)

    private fun gapFromNow(value: LocalDateTime): Duration = Duration.between(value, LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")))

    @Test
    fun 블록_안에서만_시각이_옮겨진다() {
        val inside = clock.withShift(week) { LocalDateTime.now(clock) }

        assertTrue(gapFromNow(inside) in Duration.ofDays(7)..Duration.ofDays(7).plusSeconds(5), "블록 안은 7일 과거: ${gapFromNow(inside)}")
        assertTrue(gapFromNow(LocalDateTime.now(clock)).abs() < Duration.ofSeconds(5), "블록 뒤는 실제 시각")
    }

    @Test
    fun 다른_스레드는_실제_시각을_본다() {
        val executor = Executors.newSingleThreadExecutor()
        val inBlock = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            val shifted = executor.submit<LocalDateTime> {
                clock.withShift(week) {
                    inBlock.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    LocalDateTime.now(clock)
                }
            }
            assertTrue(inBlock.await(5, TimeUnit.SECONDS))
            val other = LocalDateTime.now(clock)
            release.countDown()

            assertTrue(gapFromNow(other).abs() < Duration.ofSeconds(5), "다른 스레드는 실제 시각")
            assertTrue(gapFromNow(shifted.get(5, TimeUnit.SECONDS)) >= Duration.ofDays(7), "블록 스레드는 옮겨짐")
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun 블록이_예외로_끝나도_오프셋이_풀린다() {
        assertFailsWith<IllegalStateException> { clock.withShift(week) { error("boom") } }

        assertTrue(gapFromNow(LocalDateTime.now(clock)).abs() < Duration.ofSeconds(5))
    }

    @Test
    fun 블록을_겹쳐_써도_바깥_오프셋으로_돌아간다() {
        clock.withShift(week) {
            clock.withShift(Duration.ofDays(-14)) { }
            assertTrue(gapFromNow(LocalDateTime.now(clock)) >= Duration.ofDays(7))
            assertTrue(gapFromNow(LocalDateTime.now(clock)) < Duration.ofDays(8))
        }
    }
}
