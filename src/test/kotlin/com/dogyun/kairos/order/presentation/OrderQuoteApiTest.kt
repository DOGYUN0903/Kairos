package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.CommerceTestSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrderQuoteApiTest : CommerceTestSupport() {

    private fun skuOf(salePrice: Long): Pair<String, Long> {
        val token = newUserToken()
        approvedSeller(token)
        val productId = onSaleProduct(
            token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)), originalPrice = salePrice, salePrice = salePrice,
        )
        return token to skuIdsOf(token, productId).min()
    }

    private fun couponStatus(couponId: Long): String =
        jdbcTemplate.queryForObject("select coupon_status from coupon where id = ?", String::class.java, couponId)!!

    private fun quoteBody(items: List<Pair<Long, Int>>, productCoupons: List<Pair<Long, Long>>, cartCouponId: Long?, points: Long) = mapOf(
        "items" to items.map { mapOf("skuId" to it.first, "quantity" to it.second) },
        "productCoupons" to productCoupons.map { mapOf("skuId" to it.first, "couponId" to it.second) },
        "cartCouponId" to cartCouponId,
        "pointsToUse" to points,
    )

    @Test
    fun quote는_place와_같은_금액을_주고_쿠폰_상태를_바꾸지_않는다() {
        val (_, bSku) = skuOf(30_000)
        val (cToken, cSku) = skuOf(20_000)
        val user = newUserToken()
        val platform = claimCoupon(user, adminCouponPolicy(couponPolicyBody()))
        val seller = claimCoupon(
            user, sellerCouponPolicy(cToken, couponPolicyBody(targetType = "SELLER", discountType = "RATE", discountValue = 1_000, maxDiscount = 5_000)),
        )
        val cart = claimCoupon(
            user,
            adminCouponPolicy(
                couponPolicyBody(couponType = "CART", targetType = "NONE", discountType = "RATE", discountValue = 1_000, maxDiscount = 5_000, minOrderAmount = 50_000),
            ),
        )
        val memberId = memberIdOf(user)
        grantPoints(memberId, 20_000)
        val items = listOf(bSku to 1, cSku to 2)
        val coupons = listOf(bSku to platform, cSku to seller)

        // Idempotency-Key 없이 호출한다.
        val quote = json(post("/api/v1/orders/quote", user, quoteBody(items, coupons, cart, 10_000)).andExpect { status { isOk() } }).get("data")

        assertEquals(49_000L, quote.get("totalAmount").asLong())
        assertEquals(70_000L, quote.get("itemsAmount").asLong())
        assertEquals(6_000L, quote.get("productCouponDiscount").asLong())
        assertEquals(5_000L, quote.get("cartCouponDiscount").asLong())
        assertEquals(10_000L, quote.get("pointsUsed").asLong())
        assertEquals(0L, quote.get("buyerShippingFee").asLong())
        assertEquals(17_700L, quote.get("maxUsablePoints").asLong())
        val lines = quote.get("items").associateBy { it.get("skuId").asLong() }
        assertEquals(21_438L, lines.getValue(bSku).get("paidAmount").asLong())
        assertEquals(27_562L, lines.getValue(cSku).get("paidAmount").asLong())
        assertEquals(listOf("AVAILABLE", "AVAILABLE", "AVAILABLE"), listOf(platform, seller, cart).map(::couponStatus))
        assertEquals(20_000L, pointBalance(memberId))
        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from orders", Int::class.java))

        val placed = json(
            placeOrder(user, items, quote.get("totalAmount").asLong(), productCoupons = coupons, cartCouponId = cart, pointsToUse = 10_000)
                .andExpect { status { isCreated() } },
        ).get("data")
        assertEquals(49_000L, placed.get("totalAmount").asLong())
    }

    @Test
    fun 남의_쿠폰은_404_COUPON_NOT_FOUND() {
        val (_, sku) = skuOf(30_000)
        val owner = newUserToken()
        val other = newUserToken()
        val couponId = claimCoupon(owner, adminCouponPolicy(couponPolicyBody()))

        post("/api/v1/orders/quote", other, quoteBody(listOf(sku to 1), listOf(sku to couponId), null, 0)).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("COUPON_NOT_FOUND") }
        }
        placeOrder(other, listOf(sku to 1), 31_000, productCoupons = listOf(sku to couponId)).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("COUPON_NOT_FOUND") }
        }
        assertEquals("AVAILABLE", couponStatus(couponId))
    }

    private fun plainQuote(vararg skus: Pair<String, Long>): tools.jackson.databind.JsonNode {
        val user = newUserToken()
        return json(
            post("/api/v1/orders/quote", user, quoteBody(skus.map { it.second to 1 }, emptyList(), null, 0)).andExpect { status { isOk() } },
        ).get("data")
    }

    @Test
    fun 판매자_두_명_5만원_미만이면_판매자마다_구매자_부담_배송비_3000원이다() {
        val b = skuOf(20_000)
        val c = skuOf(20_000)

        val quote = plainQuote(b, c)

        val sellerQuotes = quote.get("sellerQuotes")
        assertEquals(2, sellerQuotes.size())
        sellerQuotes.forEach {
            assertEquals(3_000L, it.get("shippingFee").asLong())
            assertEquals("BUYER", it.get("shippingFeePayer").asText())
        }
        assertEquals(6_000L, quote.get("buyerShippingFee").asLong())
        assertEquals(46_000L, quote.get("totalAmount").asLong())
    }

    @Test
    fun 오만원_이상이면_배송비는_플랫폼_부담이다() {
        val quote = plainQuote(skuOf(30_000), skuOf(30_000))

        val sellerQuotes = quote.get("sellerQuotes")
        assertEquals(2, sellerQuotes.size())
        sellerQuotes.forEach { assertEquals("PLATFORM", it.get("shippingFeePayer").asText()) }
        assertEquals(0L, quote.get("buyerShippingFee").asLong())
    }

    @Test
    fun 판매자별_합계가_전체와_맞는다() {
        val quote = plainQuote(skuOf(20_000), skuOf(15_000))

        val sellerQuotes = quote.get("sellerQuotes").toList()
        assertEquals(quote.get("itemsAmount").asLong(), sellerQuotes.sumOf { it.get("itemsAmount").asLong() })
        assertEquals(
            quote.get("buyerShippingFee").asLong(),
            sellerQuotes.filter { it.get("shippingFeePayer").asText() == "BUYER" }.sumOf { it.get("shippingFee").asLong() },
        )
        sellerQuotes.forEach { assertTrue(it.get("sellerName").asText().isNotBlank()) }
    }

    @Test
    fun 견적_항목에_표시용_상품_정보가_있다() {
        val (_, sku) = skuOf(20_000)
        val quote = plainQuote("" to sku)

        val line = quote.get("items").single()
        assertTrue(line.get("productName").asText().isNotBlank())
        assertTrue(line.get("brandName").asText().isNotBlank())
        assertTrue(line.get("optionDisplayName").asText().isNotBlank())
        assertTrue(line.get("thumbnailUrl").asText().isNotEmpty())
        assertEquals(20_000L, line.get("unitPrice").asLong())
        assertEquals(quote.get("sellerQuotes").single().get("sellerId").asLong(), line.get("sellerId").asLong())
        assertTrue(line.get("productId").asLong() > 0)
    }

    @Test
    fun 견적에_무료배송_기준과_기본_배송비가_있다() {
        val quote = plainQuote(skuOf(20_000))

        assertEquals(50_000L, quote.get("freeShippingThreshold").asLong())
        assertEquals(3_000L, quote.get("baseShippingFee").asLong())
    }
}
