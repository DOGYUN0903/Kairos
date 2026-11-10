package com.dogyun.kairos.settlement.presentation

import com.dogyun.kairos.CommerceTestSupport
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SellerSettlementApiTest : CommerceTestSupport() {

    private class Shop(val sellerToken: String, val sellerId: Long, val skuId: Long)

    private fun shop(): Shop {
        val token = newUserToken()
        val sellerId = approvedSeller(token)
        val productId = onSaleProduct(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)), originalPrice = 20_000, salePrice = 20_000)
        return Shop(token, sellerId, skuIdsOf(token, productId).sorted()[0])
    }

    private fun confirmedAt(shop: Shop, periodStart: LocalDate, count: Int = 2) {
        val buyer = newUserToken()
        repeat(count) { i ->
            val order = confirmedOrder(buyer, shop.sellerToken, listOf(shop.skuId to 1), 23_000)
            setConfirmedAt(order.get("sellerOrders").get(0).get("sellerOrderId").asLong(), periodStart.atTime(10, i))
        }
    }

    private fun settlementId(sellerId: Long, periodStart: LocalDate): Long =
        jdbcTemplate.queryForObject("select id from settlement where seller_id = ? and period_start = ?", Long::class.java, sellerId, periodStart)!!

    @Test
    fun 판매자는_자기_정산_목록을_period_start_내림차순으로_보고_상세에_라인과_조정이_보인다() {
        val t = lastEndedPeriodStart()
        val shop = shop()
        confirmedAt(shop, t.minusDays(7))
        confirmedAt(shop, t, count = 3)
        val adj = json(
            postIdem("/api/v1/admin/sellers/${shop.sellerId}/settlement-adjustments", adminToken(), mapOf("amount" to 1_500, "reason" to "보정"))
                .andExpect { status { isCreated() } },
        ).get("data").get("adjustmentId").asLong()
        jdbcTemplate.update("update settlement_adjustment set created_at = ? where id = ?", t.plusDays(7).atStartOfDay().minusSeconds(1), adj)
        catchUpSettlements(t)

        val list = json(get("/api/v1/seller/settlements", shop.sellerToken).andExpect { status { isOk() } }).get("data")
        assertEquals(2, list.get("totalElements").asInt())
        assertEquals(t.toString(), list.get("content").get(0).get("periodStart").asText())
        assertEquals(t.minusDays(7).toString(), list.get("content").get(1).get("periodStart").asText())
        assertEquals(3, list.get("content").get(0).get("lineCount").asInt())
        assertEquals(shop.sellerId, list.get("content").get(0).get("sellerId").asLong())

        val id = settlementId(shop.sellerId, t)
        val detail = json(get("/api/v1/seller/settlements/$id", shop.sellerToken).andExpect { status { isOk() } }).get("data")
        assertEquals(3, detail.get("lines").size())
        assertEquals(1, detail.get("adjustments").size())
        assertEquals(1_500L, detail.get("adjustments").get(0).get("amount").asLong())
        assertEquals(1_500L, detail.get("adjustmentAmount").asLong())
        assertEquals("GENERATED", detail.get("status").asText())
    }

    @Test
    fun 다른_판매자_정산은_404() {
        val t = lastEndedPeriodStart()
        val a = shop()
        val b = shop()
        confirmedAt(a, t)
        catchUpSettlements(t)
        val id = settlementId(a.sellerId, t)

        get("/api/v1/seller/settlements/$id", b.sellerToken)
            .andExpect { status { isNotFound() }; jsonPath("$.code") { value("SETTLEMENT_NOT_FOUND") } }
        get("/api/v1/seller/settlements/999999999", b.sellerToken)
            .andExpect { status { isNotFound() }; jsonPath("$.code") { value("SETTLEMENT_NOT_FOUND") } }
        json(get("/api/v1/seller/settlements", b.sellerToken)).get("data").let { assertEquals(0, it.get("totalElements").asInt()) }
    }

    @Test
    fun 정지된_판매자도_확정_판매자_주문이_있으면_정산이_생기고_상세에_판매자_상태가_보인다() {
        val t = lastEndedPeriodStart()
        val shop = shop()
        confirmedAt(shop, t)
        suspendSeller(shop.sellerId)
        catchUpSettlements(t)
        val id = settlementId(shop.sellerId, t)

        json(get("/api/v1/admin/settlements/$id", adminToken())).get("data").let {
            assertEquals("SUSPENDED", it.get("sellerStatus").asText())
        }
        json(get("/api/v1/seller/settlements/$id", shop.sellerToken).andExpect { status { isOk() } }).get("data").let {
            assertEquals("SUSPENDED", it.get("sellerStatus").asText())
            assertEquals(2, it.get("lines").size())
        }
        get("/api/v1/seller/settlements", shop.sellerToken).andExpect { status { isOk() } }
    }

    @Test
    fun 일반_회원은_403() {
        val member = newUserToken()
        get("/api/v1/seller/settlements", member).andExpect { status { isForbidden() } }
        get("/api/v1/seller/settlements/1", member).andExpect { status { isForbidden() } }
        get("/api/v1/seller/settlements").andExpect { status { isUnauthorized() } }
    }
}
