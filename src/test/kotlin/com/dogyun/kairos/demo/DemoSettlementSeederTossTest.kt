package com.dogyun.kairos.demo

import com.dogyun.kairos.CommerceTestSupport
import com.dogyun.kairos.demo.application.DemoSeeder
import com.dogyun.kairos.demo.application.DemoSettlementSeeder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** demo + 토스 PG 조합에서는 시더가 실 PG 에 가짜 paymentKey 를 보내지 않도록 아무것도 만들지 않는다. */
@ActiveProfiles("test", "demo")
@TestPropertySource(properties = ["kairos.demo.password=demo-password1", "kairos.pg.provider=toss", "kairos.pg.toss.secret-key=dummy-secret"])
class DemoSettlementSeederTossTest : CommerceTestSupport() {

    @Autowired
    private lateinit var demoSeeder: DemoSeeder

    @Autowired
    private lateinit var settlementSeeder: DemoSettlementSeeder

    @BeforeTest
    fun seedProducts() {
        demoSeeder.seed()
    }

    @Test
    fun PG가_mock이_아니면_주문도_쿠폰_정책도_만들지_않는다() {
        assertEquals(0, settlementSeeder.seedLastWeek().sellerOrders)
        assertEquals(0, settlementSeeder.seedLastWeekQuietly().sellerOrders)

        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from orders", Int::class.java))
        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from coupon_policy", Int::class.java))
    }
}
