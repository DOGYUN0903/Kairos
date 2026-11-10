package com.dogyun.kairos.settlement.presentation

import com.dogyun.kairos.CommerceTestSupport
import org.junit.jupiter.api.AfterEach
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.web.servlet.post
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 시스템 시각에 오프셋을 더하는 시계. 정산 생성만 "기간이 끝난 뒤"로 보내고 DB·감사 시각(created_at)은 실제 시각 그대로 둔다. */
class OffsetClock(private val zone: ZoneId) : Clock() {
    @Volatile
    var offset: Duration = Duration.ZERO

    fun advanceTo(target: LocalDateTime) {
        offset = Duration.between(Instant.now(), target.atZone(zone).toInstant())
    }

    fun reset() {
        offset = Duration.ZERO
    }

    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = Instant.now().plus(offset)
}

@TestConfiguration
class OffsetClockConfig {
    @Bean
    @Primary
    fun offsetClock(): OffsetClock = OffsetClock(ZoneId.of("Asia/Seoul"))
}

@Import(OffsetClockConfig::class)
class AdminSettlementApiTest : CommerceTestSupport() {

    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var offsetClock: OffsetClock

    @AfterEach
    fun resetClock() = offsetClock.reset()

    private class Shop(val sellerToken: String, val sellerId: Long, val skuId: Long)

    private fun shop(): Shop {
        val token = newUserToken()
        val sellerId = approvedSeller(token)
        val productId = onSaleProduct(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)), originalPrice = 20_000, salePrice = 20_000)
        return Shop(token, sellerId, skuIdsOf(token, productId).sorted()[0])
    }

    private fun confirmedInPeriod(shop: Shop, periodStart: LocalDate, count: Int = 3) {
        val buyer = newUserToken()
        repeat(count) { i ->
            val order = confirmedOrder(buyer, shop.sellerToken, listOf(shop.skuId to 1), 23_000)
            setConfirmedAt(order.get("sellerOrders").get(0).get("sellerOrderId").asLong(), periodStart.atTime(10, i))
        }
    }

    private fun adjust(sellerId: Long, amount: Long, reason: String = "테스트 조정") =
        postIdem("/api/v1/admin/sellers/$sellerId/settlement-adjustments", adminToken(), mapOf("amount" to amount, "reason" to reason))

    private fun createAdjustment(sellerId: Long, amount: Long): Long =
        json(adjust(sellerId, amount).andExpect { status { isCreated() } }).get("data").get("adjustmentId").asLong()

    private fun setCreatedAt(adjustmentId: Long, at: LocalDateTime) {
        jdbcTemplate.update("update settlement_adjustment set created_at = ? where id = ?", at, adjustmentId)
    }

    private fun settlementId(sellerId: Long, periodStart: LocalDate): Long =
        jdbcTemplate.queryForObject("select id from settlement where seller_id = ? and period_start = ?", Long::class.java, sellerId, periodStart)!!

    private fun attachedTo(adjustmentId: Long): Long? =
        jdbcTemplate.queryForList("select settlement_id from settlement_adjustment where id = ?", adjustmentId)[0]["settlement_id"]?.let { (it as Number).toLong() }

    private fun adjustmentAmountOf(settlementId: Long): Long =
        jdbcTemplate.queryForObject("select adjustment_amount from settlement where id = ?", Long::class.java, settlementId)!!

    @Test
    fun 기간_끝_전_등록한_조정은_이번_정산_기간_끝_뒤_등록한_조정은_다음_정산에_붙는다() {
        val t = lastEndedPeriodStart()
        val shop = shop()
        confirmedInPeriod(shop, t)
        val before = createAdjustment(shop.sellerId, 1_000)
        val after = createAdjustment(shop.sellerId, 2_000)
        setCreatedAt(before, t.plusDays(7).atStartOfDay().minusSeconds(1))
        setCreatedAt(after, t.plusDays(7).atStartOfDay())

        catchUpSettlements(t)
        val s1 = settlementId(shop.sellerId, t)
        assertEquals(s1, attachedTo(before))
        assertEquals(null, attachedTo(after))
        assertEquals(1_000L, adjustmentAmountOf(s1))

        offsetClock.advanceTo(t.plusDays(14).atTime(0, 30))
        generateSettlement(adminToken(), t.plusDays(7))
        val s2 = settlementId(shop.sellerId, t.plusDays(7))
        assertEquals(s2, attachedTo(after))
        assertEquals(2_000L, adjustmentAmountOf(s2))
        assertEquals(s1, attachedTo(before))
    }

    /**
     * 시계를 앞으로 돌려(현재 주 월요일 기간을 "끝난 기간"으로 만들고) 실제 시각의 created_at 이 항상 기간 끝보다 이르게 한다.
     * 그러면 조정 10건이 생성과 경합해도 전부 "붙을 수 있는" 조정이라, 경합이 잘못되면 두 정산에 다 붙거나 합이 어긋난다.
     */
    @Test
    fun 생성과_동시에_등록한_조정은_정확히_한_정산에만_붙는다() {
        val week = lastEndedPeriodStart().plusDays(7)
        catchUpSettlements()
        val shop = shop()
        confirmedInPeriod(shop, week)
        val admin = adminToken()
        offsetClock.advanceTo(week.plusDays(7).atTime(0, 30))

        val amounts = (1..10).map { it * 100L }
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val generation = pool.submit<com.dogyun.kairos.settlement.application.GenerateSettlementResult> {
            start.await()
            settlementGenerationService.generate(week)
        }
        val posts = pool.submit<List<Int>> {
            start.await()
            amounts.map { amount ->
                mockMvc.post("/api/v1/admin/sellers/${shop.sellerId}/settlement-adjustments") {
                    header("Authorization", "Bearer $admin")
                    header("Idempotency-Key", UUID.randomUUID().toString())
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(mapOf("amount" to amount, "reason" to "경합 $amount"))
                }.andReturn().response.status
            }
        }
        start.countDown()
        val gen = generation.get()
        val statuses = posts.get()
        pool.shutdown()

        assertEquals(0, gen.failedCount)
        assertTrue(statuses.all { it == 201 }, "조정 등록이 전부 201 이어야 한다: $statuses")
        val s1 = settlementId(shop.sellerId, week)

        // 이번 정산에 붙은 조정 + (안 붙어 남은 조정이 붙는) 다음 정산의 조정 = 전체, 각 조정은 정산 하나에만.
        offsetClock.advanceTo(week.plusDays(14).atTime(0, 30))
        generateSettlement(admin, week.plusDays(7))
        val s2 = settlementId(shop.sellerId, week.plusDays(7))

        val rows = jdbcTemplate.queryForList("select id, amount, settlement_id from settlement_adjustment where seller_id = ? order by id", shop.sellerId)
        assertEquals(10, rows.size)
        rows.forEach { assertTrue((it["settlement_id"] as Number).toLong() in listOf(s1, s2), "한 정산에 붙어야 한다: $it") }
        val sumTo1 = rows.filter { (it["settlement_id"] as Number).toLong() == s1 }.sumOf { (it["amount"] as Number).toLong() }
        val sumTo2 = rows.filter { (it["settlement_id"] as Number).toLong() == s2 }.sumOf { (it["amount"] as Number).toLong() }
        assertEquals(amounts.sum(), sumTo1 + sumTo2)
        assertEquals(sumTo1, adjustmentAmountOf(s1))
        assertEquals(sumTo2, adjustmentAmountOf(s2))
    }

    @Test
    fun 음수_정산의_CARRY_OVER는_다음_정산에_붙고_정산당_최대_하나다() {
        val t = lastEndedPeriodStart()
        val shop = shop()
        confirmedInPeriod(shop, t, count = 1) // 라인 21,000
        val id = createAdjustment(shop.sellerId, -30_000)
        setCreatedAt(id, t.plusDays(7).atStartOfDay().minusSeconds(1))

        catchUpSettlements(t)
        val s1 = settlementId(shop.sellerId, t)
        val s1Row = jdbcTemplate.queryForMap("select * from settlement where id = ?", s1)
        assertEquals(-9_000L, (s1Row["total_amount"] as Number).toLong())
        assertEquals(0L, (s1Row["payable_amount"] as Number).toLong())
        val carry1 = jdbcTemplate.queryForMap("select * from settlement_adjustment where source_settlement_id = ?", s1)
        assertEquals(-9_000L, (carry1["amount"] as Number).toLong())
        assertEquals(null, carry1["settlement_id"])

        offsetClock.advanceTo(t.plusDays(14).atTime(0, 30))
        generateSettlement(adminToken(), t.plusDays(7))
        val s2 = settlementId(shop.sellerId, t.plusDays(7))
        assertEquals(s2, attachedTo((carry1["id"] as Number).toLong()))
        assertEquals(-9_000L, adjustmentAmountOf(s2))
        // 이월은 정산당 최대 하나: s2 도 음수라 하나를 더 만들고, 같은 source 가 둘인 행은 없다.
        val dup = jdbcTemplate.queryForList(
            "select source_settlement_id from settlement_adjustment where source_settlement_id is not null group by source_settlement_id having count(*) > 1",
        )
        assertTrue(dup.isEmpty())
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from settlement_adjustment where source_settlement_id = ?", Int::class.java, s2))
    }

    @Test
    fun GENERATED_PAID_직행은_409_CONFIRMED_PAID_두_번은_200() {
        val t = lastEndedPeriodStart()
        val shop = shop()
        confirmedInPeriod(shop, t)
        catchUpSettlements(t)
        val id = settlementId(shop.sellerId, t)
        val admin = adminToken()

        patch("/api/v1/admin/settlements/$id/pay", admin, mapOf("memo" to "직행"))
            .andExpect { status { isConflict() }; jsonPath("$.code") { value("SETTLEMENT_INVALID_STATE_TRANSITION") } }
        repeat(2) {
            patch("/api/v1/admin/settlements/$id/confirm", admin)
                .andExpect { status { isOk() }; jsonPath("$.data.status") { value("CONFIRMED") } }
        }
        patch("/api/v1/admin/settlements/$id/pay", admin, mapOf("memo" to "가".repeat(201)))
            .andExpect { status { isBadRequest() }; jsonPath("$.code") { value("INVALID_REQUEST") } }
        repeat(2) {
            patch("/api/v1/admin/settlements/$id/pay", admin, mapOf("memo" to "11/25 이체"))
                .andExpect {
                    status { isOk() }
                    jsonPath("$.data.status") { value("PAID") }
                    jsonPath("$.data.paidMemo") { value("11/25 이체") }
                    jsonPath("$.data.lines.length()") { value(3) }
                }
        }
        patch("/api/v1/admin/settlements/999999/confirm", admin).andExpect { status { isNotFound() } }
        patch("/api/v1/admin/settlements/$id/confirm", shop.sellerToken).andExpect { status { isForbidden() } }
    }

    @Test
    fun 관리자_목록은_sellerId_status_필터가_된다() {
        val t = lastEndedPeriodStart()
        val a = shop()
        val b = shop()
        confirmedInPeriod(a, t)
        confirmedInPeriod(b, t)
        catchUpSettlements(t)
        val admin = adminToken()
        patch("/api/v1/admin/settlements/${settlementId(a.sellerId, t)}/confirm", admin).andExpect { status { isOk() } }

        json(get("/api/v1/admin/settlements?sellerId=${a.sellerId}", admin).andExpect { status { isOk() } }).get("data").let {
            assertEquals(1, it.get("totalElements").asInt())
            assertEquals(a.sellerId, it.get("content").get(0).get("sellerId").asLong())
        }
        json(get("/api/v1/admin/settlements?status=CONFIRMED", admin)).get("data").get("content").forEach {
            assertEquals("CONFIRMED", it.get("status").asText())
        }
        json(get("/api/v1/admin/settlements?sellerId=${b.sellerId}&status=CONFIRMED", admin)).get("data").let {
            assertEquals(0, it.get("totalElements").asInt())
        }
        val detail = json(get("/api/v1/admin/settlements/${settlementId(b.sellerId, t)}", admin)).get("data")
        assertEquals(3, detail.get("lines").size())
        assertNotNull(detail.get("sellerName").asText())
        assertEquals("GENERATED", detail.get("status").asText())
    }

    @Test
    fun 조정_등록_목록_삭제_API는_검증과_상태를_지킨다() {
        val t = lastEndedPeriodStart()
        val shop = shop()
        val admin = adminToken()
        adjust(shop.sellerId, 0).andExpect { status { isBadRequest() }; jsonPath("$.code") { value("SETTLEMENT_ADJUSTMENT_INVALID") } }
        adjust(shop.sellerId, 100, " ").andExpect { status { isBadRequest() }; jsonPath("$.code") { value("SETTLEMENT_ADJUSTMENT_INVALID") } }
        adjust(9_999_999, 100).andExpect { status { isNotFound() }; jsonPath("$.code") { value("SELLER_NOT_FOUND") } }
        post("/api/v1/admin/sellers/${shop.sellerId}/settlement-adjustments", admin, mapOf("amount" to 1, "reason" to "키 없음"))
            .andExpect { status { isBadRequest() } }

        val keep = createAdjustment(shop.sellerId, 500)
        val drop = createAdjustment(shop.sellerId, 700)
        setCreatedAt(keep, t.plusDays(7).atStartOfDay().minusSeconds(1))
        json(get("/api/v1/admin/sellers/${shop.sellerId}/settlement-adjustments", admin)).get("data").let {
            assertEquals(2, it.get("totalElements").asInt())
            assertEquals(drop, it.get("content").get(0).get("adjustmentId").asLong())
        }

        delete("/api/v1/admin/settlement-adjustments/$drop", admin).andExpect { status { isNoContent() } }
        delete("/api/v1/admin/settlement-adjustments/$drop", admin).andExpect { status { isNotFound() } }

        catchUpSettlements(t)
        delete("/api/v1/admin/settlement-adjustments/$keep", admin)
            .andExpect { status { isConflict() }; jsonPath("$.code") { value("SETTLEMENT_ADJUSTMENT_APPLIED") } }
        assertEquals(settlementId(shop.sellerId, t), attachedTo(keep))
        delete("/api/v1/admin/settlement-adjustments/$keep", shop.sellerToken).andExpect { status { isForbidden() } }
    }
}
