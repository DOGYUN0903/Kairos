package com.dogyun.kairos.demo

import com.dogyun.kairos.CommerceTestSupport
import com.dogyun.kairos.demo.application.DemoProperties
import com.dogyun.kairos.demo.application.DemoSeeder
import com.dogyun.kairos.demo.application.DemoSettlementSeeder
import com.dogyun.kairos.demo.application.DemoShiftClock
import com.dogyun.kairos.demo.infrastructure.DemoDataInitializer
import com.dogyun.kairos.demo.infrastructure.DemoProfileGuard
import com.dogyun.kairos.demo.presentation.DemoController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** demo 프로필이 아니면(test 프로필) 데모 빈과 엔드포인트가 없다. */
class DemoAbsentTest : CommerceTestSupport() {

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun demo_프로필이_아니면_데모_빈이_하나도_없다() {
        listOf(
            DemoSeeder::class.java, DemoController::class.java, DemoDataInitializer::class.java,
            DemoProperties::class.java, DemoProfileGuard::class.java,
        ).forEach { type ->
            assertTrue(context.getBeanNamesForType(type).isEmpty(), "${type.simpleName} 빈이 있다")
        }
    }

    @Test
    fun demo가_아닌_프로필에는_시더_빈이_없다() {
        assertTrue(context.getBeanNamesForType(DemoSettlementSeeder::class.java).isEmpty())
    }

    @Test
    fun demo가_아니면_시스템_시계_하나다() {
        val clocks = context.getBeansOfType(Clock::class.java)
        assertEquals(1, clocks.size, "Clock 빈: ${clocks.keys}")
        assertTrue(clocks.values.none { it is DemoShiftClock })
    }

    @Test
    fun demo_프로필이_아니면_관리자의_reseed는_404() {
        post("/api/v1/demo/reseed", adminToken()).andExpect { status { isNotFound() } }
    }

    @Test
    fun demo_프로필이_아니면_비로그인_reseed는_401() {
        post("/api/v1/demo/reseed").andExpect { status { isUnauthorized() } }
    }
}
