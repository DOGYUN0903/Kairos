package com.dogyun.kairos.demo

import com.dogyun.kairos.demo.infrastructure.DemoProfileGuard
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DemoProfileGuard 가 실제 컨텍스트 기동에서 prod+demo 조합을 막는지 확인한다. */
class DemoProfileStartupTest {

    private fun runner(vararg profiles: String) = ApplicationContextRunner()
        .withInitializer { it.environment.setActiveProfiles(*profiles) }
        .withUserConfiguration(DemoProfileGuard::class.java)

    @Test
    fun prod와_demo가_동시에_활성이면_컨텍스트_기동이_실패한다() {
        runner("prod", "demo").run { context ->
            val failure = assertNotNull(context.startupFailure)
            val cause = generateSequence(failure as Throwable) { it.cause }.filterIsInstance<IllegalStateException>()
                .firstOrNull { it.message == "prod 프로필에서는 demo 프로필을 켤 수 없습니다" }
            assertNotNull(cause, "원인 체인: $failure")
        }
    }

    @Test
    fun demo만_활성이면_컨텍스트가_기동되고_가드가_등록된다() {
        runner("local", "demo").run { context ->
            assertNull(context.startupFailure)
            assertEquals(1, context.getBeansOfType(DemoProfileGuard::class.java).size)
        }
    }

    @Test
    fun prod만_활성이면_가드가_등록되지_않고_기동된다() {
        runner("prod").run { context ->
            assertNull(context.startupFailure)
            assertFalse(context.containsBean("demoProfileGuard"))
            assertTrue(context.getBeansOfType(DemoProfileGuard::class.java).isEmpty())
        }
    }
}
