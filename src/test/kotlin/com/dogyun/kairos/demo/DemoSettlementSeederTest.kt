package com.dogyun.kairos.demo

import com.dogyun.kairos.CommerceTestSupport
import com.dogyun.kairos.demo.application.DemoSeeder
import com.dogyun.kairos.demo.application.DemoSettlementSeeder
import com.dogyun.kairos.demo.application.DemoShiftClock
import com.dogyun.kairos.settlement.application.SettlementGenerationService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import java.sql.Timestamp
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 정산 시연 시더가 실제 서비스 규칙으로 C4 §8-7 예시 숫자를 만드는지 본다. 정산 시작일은 지난주 월요일로 준다. */
@ActiveProfiles("test", "demo")
@TestPropertySource(properties = ["kairos.demo.password=$SEEDER_DEMO_PASSWORD"])
class DemoSettlementSeederTest : CommerceTestSupport() {

    @Autowired
    private lateinit var demoSeeder: DemoSeeder

    @Autowired
    private lateinit var settlementSeeder: DemoSettlementSeeder

    @Autowired
    private lateinit var generationService: SettlementGenerationService

    @Autowired
    private lateinit var shiftClock: DemoShiftClock

    @Autowired
    private lateinit var context: org.springframework.context.ApplicationContext

    /** 시더와 같은 방식으로 주입된 시계에서 구한다. */
    private val lastMonday: LocalDate get() = lastMondayOf(LocalDate.now(shiftClock))

    /** 상위 cleanUp 이 카탈로그·주문을 지우므로 시연 상품을 다시 채운다(회원은 남는다). */
    @BeforeTest
    fun seedProducts() {
        demoSeeder.seed()
    }

    @Test
    fun 시더_실행_뒤_지난주_정산을_생성하면_데모_판매자의_라인이_70500_13500_마이너스3000이고_합계는_81000이다() {
        assertEquals(3, settlementSeeder.seedLastWeek().sellerOrders)

        val settlement = generateAndReadSellerSettlement()

        assertEquals(setOf(70_500L, 13_500L, -3_000L), lineAmounts(settlement).toSet())
        assertEquals(3, settlement.get("lines").size())
        assertEquals(95_000L, settlement.get("salesAmount").asLong())
        assertEquals(5_000L, settlement.get("sellerCouponDiscount").asLong())
        assertEquals(9_000L, settlement.get("commissionAmount").asLong())
        assertEquals(6_000L, settlement.get("shippingFeeOwed").asLong())
        assertEquals(6_000L, settlement.get("returnFeeBorne").asLong())
        assertEquals(81_000L, settlement.get("totalAmount").asLong())
        assertEquals(81_000L, settlement.get("payableAmount").asLong())
    }

    @Test
    fun 같은_주에_시더를_두_번_실행해도_주문_수가_같다() {
        assertEquals(3, settlementSeeder.seedLastWeek().sellerOrders)
        assertEquals(0, settlementSeeder.seedLastWeek().sellerOrders)

        assertEquals(3, sellerOrderCount())
        assertEquals(3, orderCount())
    }

    @Test
    fun 다음_주에_다시_실행하면_새_묶음을_만든다() {
        assertEquals(3, settlementSeeder.seedLastWeek().sellerOrders)

        val next = shiftClock.withShift(Duration.ofDays(7)) { settlementSeeder.seedLastWeek() }

        assertEquals(3, next.sellerOrders)
        assertEquals(6, sellerOrderCount())
        // 옮긴 시계에서의 "지난주" = 실제 이번 주. 새 주문 3건의 확정 시각이 그 안에 있다.
        val from = lastMonday.plusWeeks(1).atStartOfDay()
        val to = lastMonday.plusWeeks(2).atStartOfDay()
        val inShiftedWeek = jdbcTemplate.queryForList("select confirmed_at from seller_order", Timestamp::class.java)
            .map { it!!.toLocalDateTime() }.count { !it.isBefore(from) && it.isBefore(to) }
        assertEquals(3, inShiftedWeek)
    }

    @Test
    fun 중간에_멈춘_묶음은_다음_실행에서_다시_만들고_남은_주문이_정산에_한_줄_더_들어간다() {
        settlementSeeder.seedLastWeek()
        // 판매자 주문 1건(A)만 지난주에 확정된 상태(나머지 둘은 앞선 주로 밀려 완성 판정과 정산에서 빠진다)를 흉내 낸다.
        val keep = jdbcTemplate.queryForObject("select min(id) from seller_order", Long::class.java)!!
        jdbcTemplate.update("update seller_order set confirmed_at = confirmed_at - interval 14 day where id <> ?", keep)

        assertEquals(3, settlementSeeder.seedLastWeek().sellerOrders)

        // 알려진 동작을 고정한다: 남은 A 가 새 묶음과 함께 들어가 표준 81,000원이 아니라 151,500원이 된다.
        val settlement = generateAndReadSellerSettlement()
        assertEquals(listOf(-3_000L, 13_500L, 70_500L, 70_500L), lineAmounts(settlement).sorted())
        assertEquals(151_500L, settlement.get("totalAmount").asLong())
    }

    @Test
    fun 시연_주문의_확정_시각은_지난주_기간_안이다() {
        settlementSeeder.seedLastWeek()

        val from = lastMonday.atStartOfDay()
        val to = lastMonday.plusWeeks(1).atStartOfDay()
        val confirmed = jdbcTemplate.queryForList("select confirmed_at from seller_order", Timestamp::class.java).map { it!!.toLocalDateTime() }
        assertEquals(3, confirmed.size)
        confirmed.forEach { assertTrue(!it.isBefore(from) && it.isBefore(to), "확정 시각 $it 이 $from ~ $to 밖") }
    }

    @Test
    fun 시더가_만든_주문은_일반_주문과_같은_불변식을_만족한다() {
        settlementSeeder.seedLastWeek()

        val buyer = demoLogin("settlement-buyer@kairos.demo")
        val orderIds = jdbcTemplate.queryForList("select id from orders order by id", Long::class.java)
        assertEquals(3, orderIds.size)
        orderIds.forEach { id ->
            val order = json(get("/api/v1/orders/$id", buyer).andExpect { status { isOk() } }).get("data")
            assertMoneyInvariant(order, "시연 주문 $id")
        }
    }

    @Test
    fun 시연_상품을_더해도_기존_데모_상품_보충_수가_변하지_않는다() {
        val seller = demoLogin("seller@kairos.demo")
        assertEquals(20, mainBrandOnSale(seller))

        settlementSeeder.seedLastWeek()
        val again = demoSeeder.seed()

        assertEquals(20, mainBrandOnSale(seller))
        assertEquals(0, again.products)
        assertEquals(25, onSaleTotal(seller))
    }

    @Test
    fun 준비가_안_됐으면_예외_없이_건너뛴다() {
        val seller = demoLogin("seller@kairos.demo")
        val productId = jdbcTemplate.queryForObject("select id from product where name like '정산 시연 상품 A-1%'", Long::class.java)!!
        post("/api/v1/seller/products/$productId/end", seller).andExpect { status { isOk() } }

        assertEquals(0, settlementSeeder.seedLastWeek().sellerOrders)

        assertEquals(0, orderCount())
    }

    @Test
    fun 시더가_실패해도_reseed는_200과_평소_본문을_돌려준다() {
        // 재고를 0으로 만들어 주문 생성이 실패하게 한다(시더가 예외를 던지는 자연스러운 경로).
        jdbcTemplate.update("update sku set stock = 0")
        val admin = demoLogin("admin@kairos.demo")

        val data = json(post("/api/v1/demo/reseed", admin).andExpect { status { isOk() } }).get("data")

        for (field in listOf("members", "sellers", "brands", "categories", "products", "pendingReviews")) {
            assertTrue(data.has(field), field)
        }
        assertEquals(0, orderCount())
    }

    @Test
    fun demo_프로필이면_Clock_빈은_DemoShiftClock_하나다() {
        val clocks = context.getBeansOfType(java.time.Clock::class.java)
        assertEquals(1, clocks.size, "Clock 빈: ${clocks.keys}")
        assertTrue(clocks.values.single() is DemoShiftClock)
    }

    private fun generateAndReadSellerSettlement(): JsonNode {
        generationService.generate(lastMonday)
        val seller = demoLogin("seller@kairos.demo")
        val list = json(get("/api/v1/seller/settlements", seller).andExpect { status { isOk() } }).get("data").get("content")
        assertEquals(1, list.size())
        val id = list.get(0).get("settlementId").asLong()
        return json(get("/api/v1/seller/settlements/$id", seller).andExpect { status { isOk() } }).get("data")
    }

    private fun lineAmounts(settlement: JsonNode): List<Long> = settlement.get("lines").toList().map { it.get("lineAmount").asLong() }

    private fun sellerOrderCount() = jdbcTemplate.queryForObject("select count(*) from seller_order", Int::class.java)!!

    private fun orderCount() = jdbcTemplate.queryForObject("select count(*) from orders", Int::class.java)!!

    private fun onSaleTotal(sellerToken: String): Int =
        json(get("/api/v1/seller/products?status=ON_SALE&size=1", sellerToken)).get("data").get("totalElements").asInt()

    private fun mainBrandOnSale(sellerToken: String): Int =
        json(get("/api/v1/seller/products?status=ON_SALE&size=100", sellerToken)).get("data").get("content").toList()
            .count { it.get("brandName").asString() == "카이로스 데모" }

    private fun demoLogin(email: String): String {
        val result = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to email, "password" to SEEDER_DEMO_PASSWORD))
        }.andExpect { status { isOk() } }.andReturn()
        return json(result).get("data").get("accessToken").asString()
    }

    companion object {
        private fun lastMondayOf(today: LocalDate): LocalDate =
            today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)

        /** 컨텍스트가 뜨기 전이라 주입된 시계를 쓸 수 없어 같은 KST 기준으로 구한다(인스턴스의 [lastMonday] 와 같은 값이어야 한다). */
        private fun lastMondayKst(): LocalDate = lastMondayOf(LocalDate.now(ZoneId.of("Asia/Seoul")))

        @JvmStatic
        @DynamicPropertySource
        fun settlementStartDate(registry: DynamicPropertyRegistry) {
            registry.add("kairos.settlement.start-date") { lastMondayKst().toString() }
        }
    }
}

private const val SEEDER_DEMO_PASSWORD = "demo-password1"
