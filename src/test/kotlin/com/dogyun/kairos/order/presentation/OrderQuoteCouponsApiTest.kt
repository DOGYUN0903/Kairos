package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.CommerceTestSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrderQuoteCouponsApiTest : CommerceTestSupport() {

    private fun skuOf(salePrice: Long): Long {
        val token = newUserToken()
        approvedSeller(token)
        val productId = onSaleProduct(
            token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)), originalPrice = salePrice, salePrice = salePrice,
        )
        return skuIdsOf(token, productId).min()
    }

    private fun quote(token: String, sku: Long) =
        json(
            post(
                "/api/v1/orders/quote", token,
                mapOf("items" to listOf(mapOf("skuId" to sku, "quantity" to 1)), "productCoupons" to emptyList<Any>(), "cartCouponId" to null, "pointsToUse" to 0),
            ).andExpect { status { isOk() } },
        ).get("data")

    @Test
    fun 견적_응답에_내_쿠폰_목록이_있다() {
        val sku = skuOf(30_000)
        val user = newUserToken()
        val productCoupon = claimCoupon(user, adminCouponPolicy(couponPolicyBody()))
        val cartCoupon = claimCoupon(
            user,
            adminCouponPolicy(couponPolicyBody(couponType = "CART", targetType = "NONE", discountType = "RATE", discountValue = 1_000, maxDiscount = 5_000, minOrderAmount = 50_000)),
        )

        val data = quote(user, sku)

        val line = data.get("items").get(0).get("availableCoupons").get(0)
        assertEquals(productCoupon, line.get("couponId").asLong())
        assertEquals(true, line.get("usable").asBoolean())
        assertEquals(2_000L, line.get("expectedDiscount").asLong())
        assertEquals("PLATFORM", line.get("issuerType").asString())
        assertEquals("FIXED", line.get("discountType").asString())
        assertTrue(line.get("expiresAt").asString().isNotBlank())
        val cart = data.get("cartCoupons").get(0)
        assertEquals(cartCoupon, cart.get("couponId").asLong())
        assertEquals(false, cart.get("usable").asBoolean())
        assertEquals("MIN_AMOUNT_NOT_MET", cart.get("unusableReason").asString())
        assertTrue(cart.get("expectedDiscount").isNull)
    }

    @Test
    fun 남의_쿠폰_사용한_쿠폰_만료된_쿠폰은_목록에_없다() {
        val sku = skuOf(30_000)
        val user = newUserToken()
        val other = newUserToken()
        claimCoupon(other, adminCouponPolicy(couponPolicyBody()))
        val used = claimCoupon(user, adminCouponPolicy(couponPolicyBody()))
        val expired = claimCoupon(user, adminCouponPolicy(couponPolicyBody()))
        val alive = claimCoupon(user, adminCouponPolicy(couponPolicyBody()))
        jdbcTemplate.update("update coupon set coupon_status = 'USED' where id = ?", used)
        jdbcTemplate.update("update coupon set expires_at = now() - interval 1 day where id = ?", expired)

        val coupons = quote(user, sku).get("items").get(0).get("availableCoupons")
        val ids: List<Long> = (0 until coupons.size()).map { coupons.get(it).get("couponId").asLong() }

        assertEquals(listOf(alive), ids)
    }

    @Test
    fun 견적은_쿠폰_상태를_바꾸지_않는다() {
        val sku = skuOf(30_000)
        val user = newUserToken()
        claimCoupon(user, adminCouponPolicy(couponPolicyBody()))
        val before = get("/api/v1/coupons/me?size=100", user).andReturn().response.contentAsString

        quote(user, sku)

        val after = get("/api/v1/coupons/me?size=100", user).andReturn().response.contentAsString
        assertEquals(objectMapper.readTree(before).get("data").toString(), objectMapper.readTree(after).get("data").toString())
    }

    @Test
    fun 쿠폰이_없으면_빈_배열이다() {
        val sku = skuOf(30_000)
        val data = quote(newUserToken(), sku)

        assertEquals(0, data.get("items").get(0).get("availableCoupons").size())
        assertTrue(data.get("cartCoupons").isArray)
        assertEquals(0, data.get("cartCoupons").size())
    }
}
