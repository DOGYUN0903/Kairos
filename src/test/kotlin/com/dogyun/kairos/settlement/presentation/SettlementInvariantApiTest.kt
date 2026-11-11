package com.dogyun.kairos.settlement.presentation

import com.dogyun.kairos.CommerceTestSupport
import com.dogyun.kairos.common.application.TransactionRunner
import com.dogyun.kairos.settlement.application.ConfirmedSellerOrderReader
import com.dogyun.kairos.settlement.application.SettlementGenerationService
import com.dogyun.kairos.settlement.application.SettlementProperties
import com.dogyun.kairos.settlement.domain.Settlement
import com.dogyun.kairos.settlement.domain.SettlementAdjustmentRepository
import com.dogyun.kairos.settlement.domain.SettlementRepository
import com.dogyun.kairos.settlement.domain.SettlementRunRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * §8-8 정확성·멱등성 불변식을 실제 DB 로 증명한다: 작업이 중간에 멈추거나, 몇 번을 다시 돌거나, 둘이 동시에 돌아도 결과는 같다.
 * 기간은 start-date(S) 기준으로 고정(A = S, B = S+7)이고, 직접 만드는 서비스는 고정 시계를 쓴다. 무작위 값은 없다.
 */
class SettlementInvariantApiTest : CommerceTestSupport() {

    @Autowired private lateinit var sellerOrderReader: ConfirmedSellerOrderReader
    @Autowired private lateinit var settlementRepository: SettlementRepository
    @Autowired private lateinit var adjustmentRepository: SettlementAdjustmentRepository
    @Autowired private lateinit var runRepository: SettlementRunRepository
    @Autowired private lateinit var properties: SettlementProperties
    @Autowired private lateinit var transactionRunner: TransactionRunner

    private val periodA: LocalDate get() = properties.startDate
    private val periodB: LocalDate get() = periodA.plusDays(7)

    /** S+50 12:00 KST 고정. S+35 기간(끝 S+42)까지 생성할 수 있다. */
    private val fixedClock: Clock by lazy {
        val zone = ZoneId.of("Asia/Seoul")
        Clock.fixed(properties.startDate.plusDays(50).atTime(12, 0).atZone(zone).toInstant(), zone)
    }

    /** 실제 빈으로 만든 생성 서비스(고정 시계). [repository] 로 중단을 흉내 낸다. */
    private fun service(repository: SettlementRepository = settlementRepository) = SettlementGenerationService(
        sellerOrderReader, repository, adjustmentRepository, runRepository, properties, transactionRunner, fixedClock,
    )

    // ---------------------------------------------------------------- 픽스처

    private class Shop(val token: String, val sellerId: Long, val skus: List<Long>)

    private enum class Kind { PLAIN, CANCEL, DEFECTIVE_WITH_BENEFITS, ALL_RETURNED, ALL_DEFECTIVE }

    /** 판매자 3명: 20,000원/1000bp, 33,000원/1000bp, 12,345원/850bp(버림 확인). */
    private fun shops(): List<Shop> = listOf(20_000L to 1000, 33_000L to 1000, 12_345L to 850).map { (price, bp) ->
        val token = newUserToken()
        val sellerId = approvedSeller(token)
        jdbcTemplate.update("update seller set commission_rate_bp = ? where id = ?", bp, sellerId)
        val productId = onSaleProduct(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)), originalPrice = price, salePrice = price, stock = 50)
        Shop(token, sellerId, skuIdsOf(token, productId).sorted())
    }

    private val kindsBySeller = listOf(
        listOf(Kind.PLAIN, Kind.CANCEL, Kind.DEFECTIVE_WITH_BENEFITS, Kind.ALL_RETURNED),
        listOf(Kind.PLAIN, Kind.DEFECTIVE_WITH_BENEFITS, Kind.ALL_DEFECTIVE),
        listOf(Kind.PLAIN, Kind.CANCEL, Kind.ALL_RETURNED),
    )

    /** 판매자마다 [kindsBySeller] 의 확정 판매자 주문을 만들고 confirmed_at 을 기간 [periodStart] 안 고정 시각으로 옮긴다. */
    private fun seed(shops: List<Shop>, periodStart: LocalDate, kinds: List<List<Kind>> = kindsBySeller): List<Long> =
        shops.zip(kinds).flatMapIndexed { s, (shop, ks) ->
            ks.mapIndexed { k, kind ->
                val soId = confirmedSellerOrder(shop, kind)
                setConfirmedAt(soId, periodStart.plusDays(k.toLong()).atTime(9 + s, 30))
                soId
            }
        }

    private fun quote(user: String, items: List<Pair<Long, Int>>, productCoupons: List<Pair<Long, Long>> = emptyList(), cartCouponId: Long? = null, points: Long = 0): Long =
        json(
            post(
                "/api/v1/orders/quote", user,
                mapOf(
                    "items" to items.map { mapOf("skuId" to it.first, "quantity" to it.second) },
                    "productCoupons" to productCoupons.map { mapOf("skuId" to it.first, "couponId" to it.second) },
                    "cartCouponId" to cartCouponId,
                    "pointsToUse" to points,
                ),
            ).andExpect { status { isOk() } },
        ).get("data").get("totalAmount").asLong()

    private fun itemIdOf(order: JsonNode, skuId: Long): Long =
        order.get("sellerOrders").flatMap { it.get("items").toList() }.first { it.get("skuId").asLong() == skuId }.get("orderItemId").asLong()

    private fun confirm(user: String, order: JsonNode) {
        val orderId = order.get("orderId").asLong()
        val soId = order.get("sellerOrders").get(0).get("sellerOrderId").asLong()
        if (sellerOrderStatus(soId) != "CONFIRMED") {
            patch("/api/v1/orders/$orderId/seller-orders/$soId/confirm", user).andExpect { status { isOk() } }
        }
    }

    private fun sellerOrderStatus(soId: Long): String =
        jdbcTemplate.queryForObject("select seller_order_status from seller_order where id = ?", String::class.java, soId)!!

    /** 확정 전 취소·반품, 판매자·플랫폼 쿠폰, 포인트를 섞은 확정 판매자 주문 하나. 판매자 주문 id. */
    private fun confirmedSellerOrder(shop: Shop, kind: Kind): Long {
        val user = newUserToken()
        val order: JsonNode = when (kind) {
            Kind.PLAIN -> {
                val items = listOf(shop.skus[0] to 1)
                confirmedOrder(user, shop.token, items, quote(user, items))
            }
            Kind.CANCEL -> {
                // 결제 뒤 배송 전 항목 하나 취소 → 남은 항목만 배송·확정.
                val items = listOf(shop.skus[0] to 1, shop.skus[1] to 2)
                val paid = paidOrder(user, items, quote(user, items))
                val orderId = paid.get("orderId").asLong()
                cancelItem(user, orderId, itemIdOf(paid, shop.skus[1]))
                val soId = paid.get("sellerOrders").get(0).get("sellerOrderId").asLong()
                patch("/api/v1/seller/orders/$soId/ship", shop.token, mapOf("carrierCode" to "04", "trackingNumber" to "T-$soId-D")).andExpect { status { isOk() } }
                patch("/api/v1/seller/orders/$soId/deliver", shop.token).andExpect { status { isOk() } }
                val detail = orderDetail(user, orderId)
                confirm(user, detail)
                detail
            }
            Kind.DEFECTIVE_WITH_BENEFITS -> {
                // 판매자 쿠폰 1,000(sku0) + 플랫폼 장바구니 쿠폰 2,000 + 포인트 1,000, sku2 불량 반품 완료 뒤 확정.
                grantPoints(memberIdOf(user), 5_000)
                val sellerCoupon = claimCoupon(user, sellerCouponPolicy(shop.token, couponPolicyBody(targetType = "SELLER", discountType = "FIXED", discountValue = 1_000)))
                val cartCoupon = claimCoupon(user, adminCouponPolicy(couponPolicyBody(couponType = "CART", targetType = "NONE", discountType = "FIXED", discountValue = 2_000)))
                val items = listOf(shop.skus[0] to 2, shop.skus[2] to 1)
                val coupons = listOf(shop.skus[0] to sellerCoupon)
                val total = quote(user, items, coupons, cartCoupon, 1_000)
                val delivered = deliveredOrder(user, shop.token, items, total, productCoupons = coupons, cartCouponId = cartCoupon, pointsToUse = 1_000)
                val orderId = delivered.get("orderId").asLong()
                completedReturn(user, shop.token, orderId, itemIdOf(delivered, shop.skus[2]), "DEFECTIVE")
                val detail = orderDetail(user, orderId)
                confirm(user, detail)
                detail
            }
            Kind.ALL_DEFECTIVE -> {
                // 33,000원 1개(유료 배송) 불량 반품 → 판매자 주문이 비어 구매자 배송비 환불, 판매자 부담 반품비.
                val items = listOf(shop.skus[1] to 1)
                val delivered = deliveredOrder(user, shop.token, items, quote(user, items))
                completedReturn(user, shop.token, delivered.get("orderId").asLong(), itemIdOf(delivered, shop.skus[1]), "DEFECTIVE")
                val detail = orderDetail(user, delivered.get("orderId").asLong())
                confirm(user, detail)
                detail
            }
            Kind.ALL_RETURNED -> {
                val items = listOf(shop.skus[3] to 1)
                val delivered = deliveredOrder(user, shop.token, items, quote(user, items))
                completedReturn(user, shop.token, delivered.get("orderId").asLong(), itemIdOf(delivered, shop.skus[3]))
                val detail = orderDetail(user, delivered.get("orderId").asLong())
                confirm(user, detail)
                detail
            }
        }
        val soId = order.get("sellerOrders").get(0).get("sellerOrderId").asLong()
        assertEquals("CONFIRMED", sellerOrderStatus(soId), "kind=$kind")
        return soId
    }

    /** 수동 조정 행(created_at 고정). */
    private fun manualAdjustment(sellerId: Long, amount: Long, createdAt: LocalDateTime, refSellerOrderId: Long? = null) {
        jdbcTemplate.update(
            "insert into settlement_adjustment (seller_id, settlement_id, adjustment_type, amount, reason, ref_seller_order_id, " +
                "source_settlement_id, created_by_admin_id, created_at, created_by, modified_at, modified_by, status) " +
                "values (?, null, 'MANUAL', ?, '테스트 조정', ?, null, 1, ?, 1, ?, 1, 'ACTIVE')",
            sellerId, amount, refSellerOrderId, createdAt, createdAt,
        )
    }

    /** 판매자 3명, 기간 A 에 9개·기간 B 에 판매자 1·2 의 PLAIN 2개, 기간 A 안 조정 2건(판매자 3 은 음수 합계 → CARRY_OVER 가 B 로). */
    private fun standardFixture(): List<Shop> {
        val shops = shops()
        seed(shops, periodA)
        seed(shops.take(2), periodB, listOf(listOf(Kind.PLAIN), listOf(Kind.PLAIN)))
        manualAdjustment(shops[0].sellerId, -10_000, periodA.plusDays(1).atTime(12, 0))
        manualAdjustment(shops[2].sellerId, -200_000, periodA.plusDays(2).atTime(12, 0))
        return shops
    }

    // ---------------------------------------------------------------- 스냅샷·검사

    private fun long(v: Any?): Long = (v as Number).toLong()

    /** 생성 결과를 지운다(픽스처 조정은 남기고 붙임만 푼다; CARRY_OVER 는 생성이 만든 것이라 지운다). */
    private fun resetGeneration() {
        jdbcTemplate.update("delete from settlement_line")
        jdbcTemplate.update("delete from settlement")
        jdbcTemplate.update("delete from settlement_run")
        jdbcTemplate.update("delete from settlement_adjustment where adjustment_type = 'CARRY_OVER'")
        jdbcTemplate.update("update settlement_adjustment set settlement_id = null")
    }

    /**
     * id·created_at·modified_at 을 뺀 전체 스냅샷. 정산 id 참조는 `(seller_id, period_start)` 키로 바꿔, 지우고 다시 만든 결과와 비교할 수 있다.
     * run 의 seller_count 는 포함하고, generated_at 은 [withGeneratedAt] 일 때만 포함한다.
     */
    private fun canonical(withGeneratedAt: Boolean = true): List<List<Map<String, Any?>>> {
        val key = jdbcTemplate.queryForList("select id, seller_id, period_start from settlement")
            .associate { long(it["id"]) to "${it["seller_id"]}@${it["period_start"]}" }
        fun strip(row: Map<String, Any?>, vararg drop: String): Map<String, Any?> =
            row.entries.filter { e -> (drop.toList() + listOf("id", "created_at", "modified_at")).none { it.equals(e.key, true) } }
                .associate { it.key.lowercase() to it.value }
        val settlements = jdbcTemplate.queryForList("select * from settlement order by seller_id, period_start").map { strip(it) }
        val lines = jdbcTemplate.queryForList("select * from settlement_line order by seller_order_id")
            .map { strip(it, "settlement_id") + ("settlement" to key[long(it["settlement_id"])]) }
        val adjustments = jdbcTemplate.queryForList("select * from settlement_adjustment").map {
            // CARRY_OVER 사유의 "정산 #id" 도 자동 id 라 키로 바꾼다.
            strip(it, "settlement_id", "source_settlement_id", "reason") +
                ("reason" to Regex("#(\\d+)").replace(it["reason"] as String) { m -> "#" + key[m.groupValues[1].toLong()] }) +
                ("settlement" to (it["settlement_id"] as Number?)?.let { id -> key[id.toLong()] }) +
                ("source" to (it["source_settlement_id"] as Number?)?.let { id -> key[id.toLong()] })
        }.sortedBy { it.toString() }
        val runs = jdbcTemplate.queryForList("select * from settlement_run order by period_start")
            .map { if (withGeneratedAt) strip(it) else strip(it, "generated_at") }
        return listOf(settlements, lines, adjustments, runs)
    }

    /** 바이트 단위 비교용: settlement·line·adjustment(created/modified 제외) + settlement_run 전체 행(id 포함). */
    private fun rawRows(): List<Map<String, Any?>> =
        settlementRows() + jdbcTemplate.queryForList("select * from settlement_run order by id").map { mapOf("table" to "settlement_run") + it }

    private fun assertAllInvariants() {
        val ids = jdbcTemplate.queryForList("select id from settlement", Long::class.java)
        assertTrue(ids.isNotEmpty())
        ids.forEach { assertSettlementInvariant(it!!) }
        // 판매자 주문은 전체 정산을 통틀어 라인 1개 이하, 판매자·기간당 정산 1개, 정산당 CARRY_OVER 1개 이하.
        assertEquals(
            jdbcTemplate.queryForObject("select count(*) from settlement_line", Long::class.java),
            jdbcTemplate.queryForObject("select count(distinct seller_order_id) from settlement_line", Long::class.java),
        )
        assertEquals(
            jdbcTemplate.queryForObject("select count(*) from settlement", Long::class.java),
            jdbcTemplate.queryForObject("select count(*) from (select distinct seller_id, period_start from settlement) t", Long::class.java),
        )
        // 조정은 자기 판매자의 정산에만 붙는다.
        assertEquals(
            0L,
            jdbcTemplate.queryForObject(
                "select count(*) from settlement_adjustment a join settlement s on s.id = a.settlement_id where s.seller_id <> a.seller_id",
                Long::class.java,
            ),
        )
    }

    /**
     * 주문 테이블만으로 독립 계산한 라인(스펙 §8-2): 매출 = ORDERED 항목 (판매가+옵션가)×수량, 판매자 쿠폰은 ORDERED 항목만,
     * 수수료 = 버림(base×bp/10000), 배송비 = 판매자 주문 배송비 − 완료 반품의 구매자 배송비 환불, 반품비 = 완료 반품의 판매자 부담.
     */
    private fun expectedLine(soId: Long): Map<String, Long> {
        val so = jdbcTemplate.queryForMap("select commission_rate_bp, shipping_fee from seller_order where id = ?", soId)
        val items = jdbcTemplate.queryForList(
            "select order_item_status, sale_price, extra_price, quantity, product_coupon_discount, product_coupon_issuer from order_item where seller_order_id = ?",
            soId,
        )
        val ordered = items.filter { it["order_item_status"] == "ORDERED" }
        val sales = ordered.sumOf { (long(it["sale_price"]) + long(it["extra_price"])) * long(it["quantity"]) }
        val coupon = ordered.filter { it["product_coupon_issuer"] == "SELLER" }.sumOf { long(it["product_coupon_discount"]) }
        val returns = jdbcTemplate.queryForMap(
            "select coalesce(sum(seller_borne_fee), 0) borne, coalesce(sum(shipping_fee_refund), 0) refund from return_request " +
                "where seller_order_id = ? and return_status = 'COMPLETED'",
            soId,
        )
        val base = sales - coupon
        val commission = Math.floorDiv(base * long(so["commission_rate_bp"]), 10_000L)
        val shipping = long(so["shipping_fee"]) - long(returns["refund"])
        val borne = long(returns["borne"])
        return mapOf(
            "sales_amount" to sales, "seller_coupon_discount" to coupon, "commission_base" to base,
            "commission_amount" to commission, "shipping_fee_owed" to shipping, "return_fee_borne" to borne,
            "line_amount" to base - commission + shipping - borne,
        )
    }

    private fun settlementIdOfLine(soId: Long): Long? =
        jdbcTemplate.queryForList("select settlement_id from settlement_line where seller_order_id = ?", Long::class.java, soId).singleOrNull()

    private fun periodOfLine(soId: Long): LocalDate? = settlementIdOfLine(soId)?.let {
        jdbcTemplate.queryForObject("select period_start from settlement where id = ?", LocalDate::class.java, it)
    }

    private fun runCount(): Long = jdbcTemplate.queryForObject("select count(*) from settlement_run", Long::class.java)!!

    // ---------------------------------------------------------------- 테스트

    @Test
    fun 생성_뒤_모든_정산이_헤더_라인_합_불변식을_만족하고_라인이_주문_스냅샷에서_독립_계산한_값과_같다() {
        val shops = standardFixture()

        assertEquals(2, service().generateDue(periodB))

        assertAllInvariants()
        // 기간 안 확정 판매자 주문은 하나도 빠짐없이, 정확히 한 번 들어간다.
        val confirmed = jdbcTemplate.queryForList(
            "select id from seller_order where seller_order_status = 'CONFIRMED' and confirmed_at >= ? and confirmed_at < ? order by id",
            Long::class.java, periodA.atStartOfDay(), periodB.plusDays(7).atStartOfDay(),
        )
        val lines = jdbcTemplate.queryForList("select * from settlement_line order by seller_order_id")
        assertEquals(confirmed, lines.map { long(it["seller_order_id"]) })
        lines.forEach { line ->
            val expected = expectedLine(long(line["seller_order_id"]))
            expected.forEach { (k, v) -> assertEquals(v, long(line[k]), "seller_order_id=${line["seller_order_id"]} $k") }
        }
        // 확정 전 반품·취소·판매자 쿠폰·버림이 실제로 섞였는지(검사가 헛돌지 않게).
        assertTrue(lines.any { long(it["return_fee_borne"]) > 0 }, "판매자 부담 반품비 라인")
        assertTrue(lines.any { long(it["sales_amount"]) == 0L }, "전부 반품 라인")
        assertTrue(
            jdbcTemplate.queryForObject("select count(*) from return_request where return_status = 'COMPLETED' and shipping_fee_refund > 0", Long::class.java)!! > 0,
            "구매자 배송비 환불 반품",
        )
        assertTrue(lines.any { long(it["seller_coupon_discount"]) > 0 }, "판매자 쿠폰 라인")
        assertTrue(lines.any { long(it["commission_base"]) * 850 % 10_000 != 0L && (it["commission_rate_bp"] as Number).toInt() == 850 }, "버림 라인")
        assertTrue(
            jdbcTemplate.queryForObject("select count(*) from order_item where order_item_status = 'CANCELED'", Long::class.java)!! > 0,
            "취소 항목",
        )
        // 판매자 3: A 합계 음수 → payable 0, 그 음수가 CARRY_OVER 로 B 에 정확히 한 번 붙는다.
        val s3a = jdbcTemplate.queryForMap("select * from settlement where seller_id = ? and period_start = ?", shops[2].sellerId, periodA)
        assertTrue(long(s3a["total_amount"]) < 0)
        // (B 도 음수라 B 의 CARRY_OVER 가 다음 기간으로 하나 더 생긴다. 정산당 최대 하나.)
        val carry = jdbcTemplate.queryForList("select * from settlement_adjustment where adjustment_type = 'CARRY_OVER' and source_settlement_id = ?", long(s3a["id"]))
        assertEquals(1, carry.size)
        assertEquals(long(s3a["total_amount"]), long(carry[0]["amount"]))
        assertEquals(long(s3a["id"]), long(carry[0]["source_settlement_id"]))
        assertEquals(periodB, jdbcTemplate.queryForObject("select period_start from settlement where id = ?", LocalDate::class.java, long(carry[0]["settlement_id"])))
    }

    @Test
    fun 같은_기간_생성을_순차로_두_번_하면_settlementRows가_바이트_단위로_같고_새_행이_없다() {
        standardFixture()
        service().generateDue(periodB)
        val before = rawRows()

        assertTrue(service().generate(periodA).alreadyGenerated)
        assertTrue(service().generate(periodB).alreadyGenerated)
        assertEquals(0, service().generateDue(periodB))
        assertTrue(generateSettlement(adminToken(), periodA).get("alreadyGenerated").asBoolean())
        assertEquals(0, catchUpSettlements(periodB))

        assertEquals(before, rawRows())
    }

    @Test
    fun 동시_2회_생성은_판매자별_정산_1개이고_run_1개이며_한_번_실행과_같다() {
        standardFixture()
        val admin = adminToken()
        generateSettlement(admin, periodA) // 같은 경로(HTTP, 같은 관리자)로 한 번 실행한 기준 결과
        val single = canonical(withGeneratedAt = false)

        repeat(3) { round ->
            resetGeneration()
            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val futures = (1..2).map {
                    pool.submit<Int> {
                        start.await()
                        mockMvc.post("/api/v1/admin/settlements/generate") {
                            header("Authorization", "Bearer $admin")
                            header("Idempotency-Key", UUID.randomUUID().toString())
                            contentType = MediaType.APPLICATION_JSON
                            content = objectMapper.writeValueAsString(mapOf("periodStart" to periodA.toString()))
                        }.andReturn().response.status
                    }
                }
                start.countDown()
                assertEquals(listOf(200, 200), futures.map { it.get(60, TimeUnit.SECONDS) }, "round=$round")
            } finally {
                pool.shutdown()
            }
            assertEquals(3L, jdbcTemplate.queryForObject("select count(*) from settlement where period_start = ?", Long::class.java, periodA), "round=$round")
            assertEquals(1L, runCount(), "round=$round")
            assertAllInvariants()
            assertSameSnapshot(single, canonical(withGeneratedAt = false), "round=$round")
        }
    }

    @Test
    fun N번째_판매자에서_중단되면_앞_판매자만_커밋되고_run이_없으며_재실행_결과가_한_번에_끝난_실행과_같다() {
        standardFixture()
        service().generateDue(periodB)
        val clean = canonical()
        assertEquals(6L, jdbcTemplate.queryForObject("select count(*) from settlement", Long::class.java))

        // 정산 INSERT 가 n 번 성공한 뒤 프로세스가 죽은 것처럼 그 뒤 INSERT 를 전부 던진다(A 3개, B 3개 → n = 0..5).
        for (n in 0..5) {
            resetGeneration()
            var inserts = 0
            val crashing = object : SettlementRepository by settlementRepository {
                override fun save(s: Settlement): Settlement {
                    if (s.id == null && inserts++ >= n) throw IllegalStateException("중단 시뮬레이션 n=$n")
                    return settlementRepository.save(s)
                }
            }
            service(crashing).generateDue(periodB)

            assertEquals(n.toLong(), jdbcTemplate.queryForObject("select count(*) from settlement", Long::class.java), "n=$n")
            assertEquals(if (n >= 3) 1L else 0L, runCount(), "n=$n: 끝나지 않은 기간은 run 이 없다")
            if (n in 1..2) {
                // 판매자 id 오름차순으로 앞 판매자만 커밋됐다.
                val committed = jdbcTemplate.queryForList("select seller_id from settlement order by seller_id", Long::class.java)
                val all = jdbcTemplate.queryForList("select id from seller order by id", Long::class.java)
                assertEquals(all.filter { it in committed }, committed.take(n), "n=$n")
            }
            assertAllInvariantsIfAny()

            service().generateDue(periodB) // 재실행(한 번 더 돌려도 같아야 한다)
            service().generateDue(periodB)
            assertSameSnapshot(clean, canonical(), "n=$n: 중단 뒤 재실행 == 한 번에 끝난 실행(seller_count·generated_at 포함)")
        }
    }

    /** 스냅샷이 다르면 처음 다른 행을 보여 준다. */
    private fun assertSameSnapshot(expected: List<List<Map<String, Any?>>>, actual: List<List<Map<String, Any?>>>, label: String) {
        if (expected == actual) return
        val diff = expected.zip(actual).withIndex().first { it.value.first != it.value.second }
        val (e, a) = diff.value
        val firstDiff = (0 until maxOf(e.size, a.size)).first { e.getOrNull(it) != a.getOrNull(it) }
        throw AssertionError("$label: part=${diff.index} sizes ${e.size}/${a.size}\nexpected=${e.getOrNull(firstDiff)}\nactual  =${a.getOrNull(firstDiff)}")
    }

    private fun assertAllInvariantsIfAny() {
        if (jdbcTemplate.queryForObject("select count(*) from settlement", Long::class.java)!! > 0) assertAllInvariants()
    }

    @Test
    fun 같은_판매자_주문은_두_정산에_들어가지_않는다() {
        val shops = shops()
        val soIds = seed(shops, periodA)
        service().generate(periodA)
        val aRows = rawRows()
        val lineCount = jdbcTemplate.queryForObject("select count(*) from settlement_line", Long::class.java)

        // 이미 A 에 들어간 판매자 주문의 confirmed_at 을 B 로 옮겨도(소급 변경) B 에 다시 들어가지 않는다.
        setConfirmedAt(soIds[0], periodB.atTime(12, 0))
        setConfirmedAt(soIds.last(), periodB.atTime(13, 0))
        val b = service().generate(periodB)

        assertEquals(0, b.failedCount)
        assertEquals(2L, runCount(), "B 도 끝까지 생성돼 run 이 남는다")
        assertEquals(lineCount, jdbcTemplate.queryForObject("select count(*) from settlement_line", Long::class.java))
        assertEquals(periodA, periodOfLine(soIds[0]))
        assertEquals(periodA, periodOfLine(soIds.last()))
        assertTrue(rawRows().containsAll(aRows), "A 의 정산·라인·run 행은 그대로다")
        assertAllInvariants()
    }

    @Test
    fun 늦은_확정은_다음_기간_정산에_들어가고_lookback_밖과_start_date_전은_안_들어간다() {
        val shop = shops()[0]
        val onTime = confirmedSellerOrder(shop, Kind.PLAIN).also { setConfirmedAt(it, periodA.atTime(10, 0)) }
        service().generate(periodA)

        // A 생성 뒤 A 안 시각으로 확정된 늦은 확정 → B 로. start-date 전 확정 → 어디에도 안 들어간다.
        val late = confirmedSellerOrder(shop, Kind.PLAIN).also { setConfirmedAt(it, periodA.atTime(20, 0)) }
        val beforeStart = confirmedSellerOrder(shop, Kind.PLAIN).also { setConfirmedAt(it, periodA.minusDays(1).atTime(12, 0)) }
        service().generateDue(periodA.plusDays(28))

        assertEquals(periodA, periodOfLine(onTime))
        assertEquals(periodB, periodOfLine(late))
        assertEquals(null, periodOfLine(beforeStart))

        // S+35 의 lookback(28일) 은 [S+7, S+35): S+2 확정은 밖, S+8 확정은 안.
        val outside = confirmedSellerOrder(shop, Kind.PLAIN).also { setConfirmedAt(it, periodA.plusDays(2).atTime(9, 0)) }
        val inside = confirmedSellerOrder(shop, Kind.PLAIN).also { setConfirmedAt(it, periodA.plusDays(8).atTime(9, 0)) }
        service().generate(periodA.plusDays(35))

        assertEquals(null, periodOfLine(outside))
        assertEquals(periodA.plusDays(35), periodOfLine(inside))
        assertEquals(null, periodOfLine(beforeStart))
        assertAllInvariants()
    }

    @Test
    fun 경계_시각_확정은_반열린_구간대로_한_기간에만_들어간다() {
        val shop = shops()[0]
        val lastMicro = confirmedSellerOrder(shop, Kind.PLAIN).also { setConfirmedAt(it, periodB.atStartOfDay().minusNanos(1_000)) }
        val exactStart = confirmedSellerOrder(shop, Kind.PLAIN).also { setConfirmedAt(it, periodB.atStartOfDay()) }
        val firstA = confirmedSellerOrder(shop, Kind.PLAIN).also { setConfirmedAt(it, periodA.atStartOfDay()) }

        service().generate(periodA)
        assertEquals(periodA, periodOfLine(lastMicro))
        assertEquals(periodA, periodOfLine(firstA))
        assertEquals(null, periodOfLine(exactStart))

        service().generate(periodB)
        assertEquals(periodB, periodOfLine(exactStart))
        assertAllInvariants()
    }

    @Test
    fun 확정_뒤_변화는_라인을_바꾸지_않고_조정으로만_다음_정산에_반영된다() {
        val shops = shops()
        val soIds = seed(shops, periodA)
        service().generate(periodA)
        val aRows = rawRows()

        // 확정 뒤 주문 금액이 바뀌어도(직접 UPDATE) 라인은 그대로, CS 보상은 조정으로 다음 기간에.
        jdbcTemplate.update("update order_item set sale_price = 1 where seller_order_id = ?", soIds[0])
        manualAdjustment(shops[0].sellerId, -3_000, periodB.atTime(12, 0), refSellerOrderId = soIds[0])
        service().generate(periodB)

        assertTrue(rawRows().containsAll(aRows.filter { it["table"] != "settlement_adjustment" }), "A 정산·라인 불변")
        val adj = jdbcTemplate.queryForMap("select * from settlement_adjustment where ref_seller_order_id = ?", soIds[0])
        assertEquals(periodB, jdbcTemplate.queryForObject("select period_start from settlement where id = ?", LocalDate::class.java, long(adj["settlement_id"])))
        assertEquals(periodA, periodOfLine(soIds[0]))
        assertAllInvariants()
    }

    @Test
    fun 기간_A_B를_순서대로_만들든_generateDue가_한_번에_만들든_settlementRows가_같다() {
        standardFixture()
        service().generate(periodA)
        service().generate(periodB)
        val stepByStep = canonical()

        resetGeneration()
        service().generateDue(periodB)

        assertSameSnapshot(stepByStep, canonical(), "A→B vs generateDue")
        assertAllInvariants()
    }
}
