package com.dogyun.kairos.benefit.presentation

import com.dogyun.kairos.CommerceTestSupport
import org.springframework.test.web.servlet.ResultActionsDsl
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CouponApiTest : CommerceTestSupport() {

    private class Shop(val sellerToken: String, val sellerId: Long, val productId: Long)

    private fun shop(): Shop {
        val token = newUserToken()
        val sellerId = approvedSeller(token)
        val productId = onSaleProduct(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)))
        return Shop(token, sellerId, productId)
    }

    private fun ResultActionsDsl.expectCode(status: Int, code: String) {
        andExpect {
            status { isEqualTo(status) }
            jsonPath("$.code") { value(code) }
        }
    }

    private fun content(path: String, token: String): JsonNode =
        json(get(path, token).andExpect { status { isOk() } }).get("data").get("content")

    private fun ids(content: JsonNode, field: String = "policyId"): List<Long> =
        (0 until content.size()).map { content.get(it).get(field).asLong() }

    @Test
    fun 관리자_정책을_만들고_사용자가_받으면_내_쿠폰에_AVAILABLE로_보인다() {
        val policyId = adminCouponPolicy(couponPolicyBody(validityDays = 7))
        val user = newUserToken()

        val listed = content("/api/v1/coupons/policies", user)
        assertEquals(listOf(policyId), ids(listed))
        assertFalse(listed.get(0).get("claimed").asBoolean())

        val couponId = claimCoupon(user, policyId)

        val mine = content("/api/v1/coupons/me", user)
        assertEquals(1, mine.size())
        assertEquals(couponId, mine.get(0).get("couponId").asLong())
        assertEquals("AVAILABLE", mine.get(0).get("status").asString())
        assertEquals(2000L, mine.get(0).get("discountValue").asLong())
        assertEquals(1, content("/api/v1/coupons/me?status=AVAILABLE", user).size())
        assertEquals(0, content("/api/v1/coupons/me?status=USED", user).size())
        assertTrue(content("/api/v1/coupons/policies", user).get(0).get("claimed").asBoolean())
        assertEquals(1, json(get("/api/v1/admin/coupons/policies", adminToken())).get("data").get("content").size())
    }

    @Test
    fun 총_수량_1인_정책에_동시_발급_10건은_1건만_201이고_issued_count는_1이다() {
        val policyId = adminCouponPolicy(couponPolicyBody(totalQuantity = 1))
        val tokens = (1..10).map { newUserToken() }
        val pool = Executors.newFixedThreadPool(10)
        val start = CountDownLatch(1)
        val futures = tokens.map { token ->
            pool.submit<Pair<Int, String>> {
                start.await()
                val result = postIdem("/api/v1/coupons/policies/$policyId/claim", token, null).andReturn()
                result.response.status to result.response.contentAsString
            }
        }
        start.countDown()
        val results = futures.map { it.get() }
        pool.shutdown()

        assertEquals(1, results.count { it.first == 201 })
        val rejected = results.filter { it.first != 201 }
        assertEquals(9, rejected.size)
        assertTrue(rejected.all { it.first == 409 && it.second.contains("COUPON_SOLD_OUT") })
        assertEquals(1, jdbcTemplate.queryForObject("select issued_count from coupon_policy where id = ?", Int::class.java, policyId))
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from coupon where policy_id = ?", Int::class.java, policyId))
    }

    @Test
    fun 같은_회원_재발급은_409_COUPON_ALREADY_ISSUED() {
        val policyId = adminCouponPolicy(couponPolicyBody(totalQuantity = 5))
        val user = newUserToken()
        claimCoupon(user, policyId)

        postIdem("/api/v1/coupons/policies/$policyId/claim", user, null).expectCode(409, "COUPON_ALREADY_ISSUED")

        assertEquals(1, jdbcTemplate.queryForObject("select issued_count from coupon_policy where id = ?", Int::class.java, policyId))
    }

    @Test
    fun 판매자_정책은_상품_상세용_목록에_그_판매자_상품일_때만_나온다() {
        val mine = shop()
        val other = shop()
        val sellerPolicy = sellerCouponPolicy(mine.sellerToken, couponPolicyBody(targetType = "SELLER"))
        val productPolicy = sellerCouponPolicy(
            mine.sellerToken, couponPolicyBody(targetType = "PRODUCT", targetProductId = mine.productId),
        )
        val user = newUserToken()

        assertEquals(emptyList(), ids(content("/api/v1/coupons/policies", user)))
        assertEquals(
            setOf(sellerPolicy, productPolicy),
            ids(content("/api/v1/coupons/policies?productId=${mine.productId}", user)).toSet(),
        )
        assertEquals(emptyList(), ids(content("/api/v1/coupons/policies?productId=${other.productId}", user)))
        assertEquals(
            setOf(sellerPolicy, productPolicy),
            ids(content("/api/v1/seller/coupons/policies", mine.sellerToken)).toSet(),
        )
        get("/api/v1/coupons/policies?productId=999999", user).expectCode(404, "PRODUCT_NOT_FOUND")
    }

    @Test
    fun 종료한_정책은_목록에서_빠지고_발급은_409_COUPON_POLICY_ENDED() {
        val shop = shop()
        val policyId = sellerCouponPolicy(shop.sellerToken, couponPolicyBody(targetType = "SELLER"))
        val user = newUserToken()

        patch("/api/v1/seller/coupons/policies/$policyId/end", shop.sellerToken).andExpect { status { isOk() } }
        patch("/api/v1/seller/coupons/policies/$policyId/end", shop.sellerToken).andExpect { status { isOk() } }

        assertEquals(emptyList(), ids(content("/api/v1/coupons/policies?productId=${shop.productId}", user)))
        postIdem("/api/v1/coupons/policies/$policyId/claim", user, null).expectCode(409, "COUPON_POLICY_ENDED")
        patch("/api/v1/seller/coupons/policies/$policyId/end", approvedOtherSellerToken()).expectCode(404, "COUPON_POLICY_NOT_FOUND")

        val platform = adminCouponPolicy(couponPolicyBody())
        patch("/api/v1/admin/coupons/policies/$platform/end", adminToken()).andExpect { status { isOk() } }
        postIdem("/api/v1/coupons/policies/$platform/claim", user, null).expectCode(409, "COUPON_POLICY_ENDED")
    }

    private fun approvedOtherSellerToken(): String = newUserToken().also { approvedSeller(it) }

    @Test
    fun 만료된_쿠폰은_내_쿠폰에_EXPIRED로_보인다() {
        val policyId = adminCouponPolicy(couponPolicyBody())
        val user = newUserToken()
        val couponId = claimCoupon(user, policyId)

        jdbcTemplate.update("update coupon set expires_at = ? where id = ?", LocalDateTime.now().minusDays(1), couponId)

        val mine = content("/api/v1/coupons/me", user)
        assertEquals("EXPIRED", mine.get(0).get("status").asString())
        assertEquals(1, content("/api/v1/coupons/me?status=EXPIRED", user).size())
        assertEquals(0, content("/api/v1/coupons/me?status=AVAILABLE", user).size())
        get("/api/v1/coupons/me?status=BOGUS", user).expectCode(400, "INVALID_REQUEST")
    }

    @Test
    fun 일반_회원의_판매자_정책_생성은_403() {
        val user = newUserToken()
        postIdem("/api/v1/seller/coupons/policies", user, couponPolicyBody(targetType = "SELLER")).andExpect { status { isForbidden() } }
        postIdem("/api/v1/admin/coupons/policies", user, couponPolicyBody()).andExpect { status { isForbidden() } }
    }

    @Test
    fun 판매자가_남의_상품을_대상으로_하면_400_COUPON_POLICY_INVALID이고_저장되지_않는다() {
        val mine = shop()
        val other = shop()

        postIdem(
            "/api/v1/seller/coupons/policies", mine.sellerToken,
            couponPolicyBody(targetType = "PRODUCT", targetProductId = other.productId),
        ).expectCode(400, "COUPON_POLICY_INVALID")

        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from coupon_policy", Int::class.java))
    }

    @Test
    fun 같은_Idempotency_Key로_재시도하면_발급은_한_번이다() {
        val policyId = adminCouponPolicy(couponPolicyBody(totalQuantity = 5))
        val user = newUserToken()
        val key = "claim-key-1"

        val first = postIdem("/api/v1/coupons/policies/$policyId/claim", user, null, key).andExpect { status { isCreated() } }
        val second = postIdem("/api/v1/coupons/policies/$policyId/claim", user, null, key).andExpect { status { isCreated() } }

        assertEquals(json(first).get("data").get("couponId").asLong(), json(second).get("data").get("couponId").asLong())
        assertEquals(1, jdbcTemplate.queryForObject("select issued_count from coupon_policy where id = ?", Int::class.java, policyId))
    }

    @Test
    fun 정률_정책에_최대_할인이_없으면_400() {
        postIdem(
            "/api/v1/admin/coupons/policies", adminToken(),
            couponPolicyBody(discountType = "RATE", discountValue = 1000, maxDiscount = null),
        ).expectCode(400, "COUPON_POLICY_INVALID")
    }
}
