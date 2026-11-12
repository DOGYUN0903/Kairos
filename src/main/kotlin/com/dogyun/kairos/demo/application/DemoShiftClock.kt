package com.dogyun.kairos.demo.application

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * demo 프로필 전용 시계. 평소에는 시스템 시계(KST)와 같고, [withShift] 블록을 실행하는 **그 스레드에서만** 시각이 옮겨진다.
 * 다른 스레드(요청·스케줄러)는 항상 실제 시각을 본다. 순수 java.time 클래스라 application 계층에 둔다.
 * 오프셋은 더하는 값이다(과거로 옮기려면 음수).
 */
@Component
@Profile("demo")
class DemoShiftClock private constructor(
    private val zone: ZoneId,
    private val offsets: ThreadLocal<Duration>,
) : Clock() {

    constructor() : this(ZoneId.of("Asia/Seoul"), ThreadLocal.withInitial { Duration.ZERO })

    /** 블록을 실행하는 동안만 이 스레드의 시각을 [offset] 만큼 옮긴다. 예외로 끝나도, 겹쳐 써도 바깥 값으로 돌아간다. */
    fun <T> withShift(offset: Duration, block: () -> T): T {
        val previous = offsets.get()
        offsets.set(offset)
        try {
            return block()
        } finally {
            offsets.set(previous)
        }
    }

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = if (zone == this.zone) this else DemoShiftClock(zone, offsets)

    override fun instant(): Instant = Instant.now().plus(offsets.get())
}
