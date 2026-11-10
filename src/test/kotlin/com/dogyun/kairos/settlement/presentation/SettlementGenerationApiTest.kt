package com.dogyun.kairos.settlement.presentation

import com.dogyun.kairos.CommerceTestSupport
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettlementGenerationApiTest : CommerceTestSupport() {

    private class Shop(val sellerToken: String, val sellerId: Long, val productId: Long, val skuId: Long)

    private fun shop(salePrice: Long = 20_000): Shop {
        val token = newUserToken()
        val sellerId = approvedSeller(token)
        val productId = onSaleProduct(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)), originalPrice = salePrice, salePrice = salePrice)
        return Shop(token, sellerId, productId, skuIdsOf(token, productId).sorted()[0])
    }

    /** 판매자 주문 3개(각 20,000원 1개, 배송비 3,000)를 확정하고 확정 시각을 기간 [periodStart] 안으로 옮긴다. */
    private fun confirmedInPeriod(shop: Shop, periodStart: LocalDate, count: Int = 3): List<Long> {
        val buyer = newUserToken()
        return (0 until count).map { i ->
            val order = confirmedOrder(buyer, shop.sellerToken, listOf(shop.skuId to 1), 23_000)
            val soId = order.get("sellerOrders").get(0).get("sellerOrderId").asLong()
            setConfirmedAt(soId, periodStart.atTime(10, i))
            soId
        }
    }

    /** 수동 조정 행을 넣는다(created_at 은 기간 안). 조정 API 는 다음 작업에서 생긴다. */
    private fun manualAdjustment(sellerId: Long, amount: Long, createdAt: java.time.LocalDateTime) {
        jdbcTemplate.update(
            "insert into settlement_adjustment (seller_id, settlement_id, adjustment_type, amount, reason, ref_seller_order_id, " +
                "source_settlement_id, created_by_admin_id, created_at, created_by, modified_at, modified_by, status) " +
                "values (?, null, 'MANUAL', ?, '테스트 조정', null, null, 1, ?, 1, ?, 1, 'ACTIVE')",
            sellerId, amount, createdAt, createdAt,
        )
    }

    private fun lines(): List<Map<String, Any?>> = settlementRows().filter { it["table"] == "settlement_line" }

    private fun settlementOf(sellerId: Long, periodStart: LocalDate): Map<String, Any?> =
        jdbcTemplate.queryForMap("select * from settlement where seller_id = ? and period_start = ?", sellerId, periodStart)

    @Test
    fun 확정_판매자_주문을_지난_기간으로_옮기고_생성하면_라인_금액이_스펙_8_7과_같다() {
        val target = lastEndedPeriodStart()
        val shop = shop()
        val soIds = confirmedInPeriod(shop, target)
        manualAdjustment(shop.sellerId, -10_000, target.atTime(12, 0))
        catchUpSettlements(target.minusDays(7))

        val r: JsonNode = generateSettlement(adminToken(), target)

        assertEquals(1, r.get("createdCount").asInt())
        assertEquals(false, r.get("alreadyGenerated").asBoolean())
        val ls = lines()
        assertEquals(soIds, ls.map { (it["seller_order_id"] as Number).toLong() })
        // 라인 = 판매가 − 수수료 10%(버림) + 배송비 − 판매자 귀책 반품비 = 20,000 − 2,000 + 3,000 − 0
        ls.forEach {
            assertEquals(20_000L, (it["sales_amount"] as Number).toLong())
            assertEquals(2_000L, (it["commission_amount"] as Number).toLong())
            assertEquals(3_000L, (it["shipping_fee_owed"] as Number).toLong())
            assertEquals(21_000L, (it["line_amount"] as Number).toLong())
        }
        val s = settlementOf(shop.sellerId, target)
        assertEquals(-10_000L, (s["adjustment_amount"] as Number).toLong())
        assertEquals(53_000L, (s["total_amount"] as Number).toLong())
        assertEquals(53_000L, (s["payable_amount"] as Number).toLong())
        assertEquals(3, (s["line_count"] as Number).toInt())
    }

    @Test
    fun 직전_기간_미생성_상태에서_요청하면_400_SETTLEMENT_PERIOD_INVALID() {
        postIdem("/api/v1/admin/settlements/generate", adminToken(), mapOf("periodStart" to lastEndedPeriodStart().toString()))
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("SETTLEMENT_PERIOD_INVALID") }
            }
    }

    @Test
    fun catchUp_뒤_같은_기간_POST_두_번은_두_번째가_새_행_0개이고_settlementRows가_같다() {
        val target = lastEndedPeriodStart()
        val shop = shop()
        confirmedInPeriod(shop, target)
        catchUpSettlements(target.minusDays(7))
        generateSettlement(adminToken(), target)
        val before = settlementRows()

        val again = generateSettlement(adminToken(), target)

        assertTrue(again.get("alreadyGenerated").asBoolean())
        assertEquals(0, again.get("createdCount").asInt())
        assertEquals(before, settlementRows())
        assertEquals(0, catchUpSettlements())
        assertEquals(before, settlementRows())
    }

    @Test
    fun 판매자의_현재_수수료율과_상품_가격을_바꿔도_라인이_같다() {
        val target = lastEndedPeriodStart()
        val shop = shop()
        confirmedInPeriod(shop, target)
        catchUpSettlements()
        val before = settlementRows()

        jdbcTemplate.update("update seller set commission_rate_bp = 3000 where id = ?", shop.sellerId)
        jdbcTemplate.update("update product set sale_price = 99000 where id = ?", shop.productId)
        assertTrue(generateSettlement(adminToken(), target).get("alreadyGenerated").asBoolean())

        assertEquals(before, settlementRows())
    }

    @Test
    fun 생성_뒤_order_item_금액을_직접_UPDATE해도_라인이_같다() {
        val target = lastEndedPeriodStart()
        val shop = shop()
        val soIds = confirmedInPeriod(shop, target)
        catchUpSettlements()
        val before = settlementRows()

        jdbcTemplate.update("update order_item set sale_price = 1 where seller_order_id = ?", soIds[0])
        catchUpSettlements()

        assertEquals(before, settlementRows())
    }

    @Test
    fun 일반_회원_판매자_토큰은_403() {
        val shop = shop()
        postIdem("/api/v1/admin/settlements/generate", newUserToken(), mapOf("periodStart" to lastEndedPeriodStart().toString()))
            .andExpect { status { isForbidden() } }
        postIdem("/api/v1/admin/settlements/generate", shop.sellerToken, mapOf("periodStart" to lastEndedPeriodStart().toString()))
            .andExpect { status { isForbidden() } }
    }
}
