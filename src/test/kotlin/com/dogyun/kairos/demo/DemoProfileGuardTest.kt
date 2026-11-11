package com.dogyun.kairos.demo

import com.dogyun.kairos.demo.infrastructure.DemoProfileGuard
import org.springframework.mock.env.MockEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DemoProfileGuardTest {

    @Test
    fun prod와_demo가_동시에_활성이면_생성자가_IllegalStateException() {
        val env = MockEnvironment().apply { setActiveProfiles("prod", "demo") }

        val e = assertFailsWith<IllegalStateException> { DemoProfileGuard(env) }
        assertEquals("prod 프로필에서는 demo 프로필을 켤 수 없습니다", e.message)
    }

    @Test
    fun demo만_활성이면_통과() {
        DemoProfileGuard(MockEnvironment().apply { setActiveProfiles("local", "demo") })
    }
}
