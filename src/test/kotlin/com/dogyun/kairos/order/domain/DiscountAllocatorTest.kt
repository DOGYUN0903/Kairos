package com.dogyun.kairos.order.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DiscountAllocatorTest {

    private val policy = PointUsePolicy(minUsableBalance = 1_000, useUnit = 10, maxUseRateBp = 3_000)

    private fun assertCode(code: ErrorCode, block: () -> Unit) {
        assertEquals(code, assertFailsWith<DomainException> { block() }.errorCode)
    }

    private fun productCoupon(
        id: Long, issuer: CouponIssuer, type: CouponDiscountType, value: Long, max: Long? = null, min: Long? = null,
        target: CouponTarget = CouponTarget.ALL_PRODUCTS, productId: Long? = null, sellerId: Long? = null,
    ) = CouponTerms(id, CouponKind.PRODUCT, issuer, target, productId, sellerId, type, value, max, min)

    private fun cartCoupon(id: Long = 99, type: CouponDiscountType, value: Long, max: Long? = null, min: Long? = null) =
        CouponTerms(id, CouponKind.CART, CouponIssuer.PLATFORM, CouponTarget.ALL_PRODUCTS, null, null, type, value, max, min)

    private val b1 = line(sellerId = 2, price = 30_000, skuId = 21)
    private val c1 = line(sellerId = 3, price = 20_000, quantity = 2, skuId = 31)

    @Test
    fun 스펙_예시_b1_c1에서_배분과_paidAmount가_21438_27562이고_합이_49000이다() {
        val a = DiscountAllocator.allocate(
            lines = listOf(c1, b1),
            productCoupons = mapOf(
                21L to productCoupon(1, CouponIssuer.PLATFORM, CouponDiscountType.FIXED, 2_000),
                31L to productCoupon(2, CouponIssuer.SELLER, CouponDiscountType.RATE, 1_000, max = 5_000),
            ),
            cartCoupon = cartCoupon(type = CouponDiscountType.RATE, value = 1_000, max = 5_000, min = 50_000),
            pointsToUse = 10_000, pointBalance = 20_000, policy = policy,
        )

        val b = a.lines.getValue(21)
        val c = a.lines.getValue(31)
        assertEquals(2_000, b.productCouponDiscount)
        assertEquals(4_000, c.productCouponDiscount)
        assertEquals(CouponIssuer.SELLER, c.productCouponIssuer)
        assertEquals(2_187, b.cartCouponAllocated)
        assertEquals(2_813, c.cartCouponAllocated)
        assertEquals(4_375, b.pointsAllocated)
        assertEquals(5_625, c.pointsAllocated)
        assertEquals(6_000, a.productCouponDiscount)
        assertEquals(5_000, a.cartCouponDiscount)
        assertEquals(99L, a.cartCouponId)
        assertEquals(10_000, a.pointsUsed)
        val paidB = 30_000 - b.productCouponDiscount - b.cartCouponAllocated - b.pointsAllocated
        val paidC = 40_000 - c.productCouponDiscount - c.cartCouponAllocated - c.pointsAllocated
        assertEquals(21_438, paidB)
        assertEquals(27_562, paidC)
        assertEquals(49_000, paidB + paidC)
    }

    @Test
    fun `3개 항목에 10000원 할인은 3333 3333 3334로 나뉘고 합이 정확히 10000이다`() {
        val lines = listOf(line(1, 10_000, skuId = 11), line(2, 10_000, skuId = 21), line(3, 10_000, skuId = 31))

        val a = DiscountAllocator.allocate(
            lines, emptyMap(), cartCoupon(type = CouponDiscountType.FIXED, value = 10_000), 0, 0, policy,
        )

        assertEquals(listOf(3_333L, 3_333L, 3_334L), listOf(11L, 21L, 31L).map { a.lines.getValue(it).cartCouponAllocated })
        assertEquals(10_000, a.lines.values.sumOf { it.cartCouponAllocated })
    }

    @Test
    fun 정률_할인은_버림이고_최대_할인으로_자른다() {
        val rate = productCoupon(1, CouponIssuer.PLATFORM, CouponDiscountType.RATE, 333)
        assertEquals(999, rate.discountFor(30_000)) // 30,000 * 3.33% = 999.0
        assertEquals(333, rate.discountFor(10_000)) // 333.0
        assertEquals(33, rate.discountFor(1_000)) // 33.3 -> 33
        val capped = productCoupon(2, CouponIssuer.PLATFORM, CouponDiscountType.RATE, 1_000, max = 2_000)
        assertEquals(2_000, capped.discountFor(50_000))
        assertEquals(0, capped.discountFor(0))
        val fixed = productCoupon(3, CouponIssuer.PLATFORM, CouponDiscountType.FIXED, 5_000)
        assertEquals(3_000, fixed.discountFor(3_000))
    }

    @Test
    fun 상품_쿠폰_최소_금액은_lineAmount_기준이다() {
        val coupon = productCoupon(1, CouponIssuer.PLATFORM, CouponDiscountType.FIXED, 1_000, min = 30_001)
        assertCode(ErrorCode.COUPON_MIN_AMOUNT_NOT_MET) {
            DiscountAllocator.allocate(listOf(b1), mapOf(21L to coupon), null, 0, 0, policy)
        }
        val ok = productCoupon(1, CouponIssuer.PLATFORM, CouponDiscountType.FIXED, 1_000, min = 30_000)
        assertEquals(1_000, DiscountAllocator.allocate(listOf(b1), mapOf(21L to ok), null, 0, 0, policy).productCouponDiscount)
    }

    @Test
    fun 장바구니_쿠폰_최소_금액은_상품_쿠폰_차감_후_합계_기준이다() {
        val product = productCoupon(1, CouponIssuer.PLATFORM, CouponDiscountType.FIXED, 2_000)
        val cart = cartCoupon(type = CouponDiscountType.FIXED, value = 1_000, min = 50_000)
        val lines = listOf(b1, line(3, 20_000, skuId = 31)) // 합 50,000, 상품 쿠폰 후 48,000
        assertCode(ErrorCode.COUPON_MIN_AMOUNT_NOT_MET) {
            DiscountAllocator.allocate(lines, mapOf(21L to product), cart, 0, 0, policy)
        }
        assertEquals(1_000, DiscountAllocator.allocate(lines, emptyMap(), cart, 0, 0, policy).cartCouponDiscount)
    }

    @Test
    fun 대상이_아닌_상품에_상품_쿠폰을_쓰면_COUPON_NOT_APPLICABLE() {
        val coupon = productCoupon(1, CouponIssuer.SELLER, CouponDiscountType.FIXED, 1_000, target = CouponTarget.SELLER, sellerId = 9)
        assertCode(ErrorCode.COUPON_NOT_APPLICABLE) { DiscountAllocator.allocate(listOf(b1), mapOf(21L to coupon), null, 0, 0, policy) }
        val byProduct = productCoupon(2, CouponIssuer.SELLER, CouponDiscountType.FIXED, 1_000, target = CouponTarget.PRODUCT, productId = 1)
        assertCode(ErrorCode.COUPON_NOT_APPLICABLE) { DiscountAllocator.allocate(listOf(b1), mapOf(21L to byProduct), null, 0, 0, policy) }
        val none = productCoupon(3, CouponIssuer.SELLER, CouponDiscountType.FIXED, 1_000, target = CouponTarget.NONE)
        assertCode(ErrorCode.COUPON_NOT_APPLICABLE) { DiscountAllocator.allocate(listOf(b1), mapOf(21L to none), null, 0, 0, policy) }
        // 주문에 없는 SKU 에 건 쿠폰
        val any = productCoupon(4, CouponIssuer.PLATFORM, CouponDiscountType.FIXED, 1_000)
        assertCode(ErrorCode.COUPON_NOT_APPLICABLE) { DiscountAllocator.allocate(listOf(b1), mapOf(77L to any), null, 0, 0, policy) }
    }

    @Test
    fun 장바구니_자리에_상품_쿠폰을_넣으면_COUPON_NOT_APPLICABLE() {
        val product = productCoupon(1, CouponIssuer.PLATFORM, CouponDiscountType.FIXED, 1_000)
        assertCode(ErrorCode.COUPON_NOT_APPLICABLE) { DiscountAllocator.allocate(listOf(b1), emptyMap(), product, 0, 0, policy) }
        assertCode(ErrorCode.COUPON_NOT_APPLICABLE) {
            DiscountAllocator.allocate(listOf(b1), mapOf(21L to cartCoupon(type = CouponDiscountType.FIXED, value = 1_000)), null, 0, 0, policy)
        }
    }

    @Test
    fun 포인트는_10_단위_아니면_POINT_INVALID_AMOUNT() {
        assertCode(ErrorCode.POINT_INVALID_AMOUNT) { DiscountAllocator.allocate(listOf(b1), emptyMap(), null, 1_005, 5_000, policy) }
        assertCode(ErrorCode.POINT_INVALID_AMOUNT) { DiscountAllocator.allocate(listOf(b1), emptyMap(), null, -10, 5_000, policy) }
    }

    @Test
    fun 잔액_1000_미만이면_POINT_EXCEEDS_LIMIT() {
        assertCode(ErrorCode.POINT_EXCEEDS_LIMIT) { DiscountAllocator.allocate(listOf(b1), emptyMap(), null, 500, 999, policy) }
        assertEquals(0, DiscountAllocator.maxUsablePoints(30_000, 999, policy))
    }

    @Test
    fun `30퍼센트 상한은 10P 단위로 내린다`() {
        assertEquals(17_700, DiscountAllocator.maxUsablePoints(59_000, 100_000, policy))
        assertEquals(17_990, DiscountAllocator.maxUsablePoints(59_990, 100_000, policy))
        assertEquals(17_990, DiscountAllocator.maxUsablePoints(59_999, 100_000, policy)) // 17,999.7 -> 17,999 -> 17,990
        val lines = listOf(line(1, 59_990, skuId = 11))
        assertEquals(17_990, DiscountAllocator.allocate(lines, emptyMap(), null, 17_990, 100_000, policy).pointsUsed)
        assertCode(ErrorCode.POINT_EXCEEDS_LIMIT) { DiscountAllocator.allocate(lines, emptyMap(), null, 18_000, 100_000, policy) }
    }

    @Test
    fun 쿠폰_적용_후_금액이_0이면_장바구니_쿠폰과_포인트를_거절한다() {
        val full = productCoupon(1, CouponIssuer.PLATFORM, CouponDiscountType.FIXED, 30_000)
        val cart = cartCoupon(type = CouponDiscountType.FIXED, value = 1_000)
        assertCode(ErrorCode.COUPON_NOT_APPLICABLE) { DiscountAllocator.allocate(listOf(b1), mapOf(21L to full), cart, 0, 0, policy) }
        assertCode(ErrorCode.POINT_EXCEEDS_LIMIT) { DiscountAllocator.allocate(listOf(b1), mapOf(21L to full), null, 1_000, 5_000, policy) }
        assertEquals(0, DiscountAllocator.maxUsablePoints(0, 5_000, policy))
    }

    @Test
    fun 잔액보다_많이_쓰면_POINT_INSUFFICIENT() {
        assertCode(ErrorCode.POINT_INSUFFICIENT) { DiscountAllocator.allocate(listOf(b1), emptyMap(), null, 5_000, 2_000, policy) }
    }

    @Test
    fun maxUsablePoints는_잔액과_상한_중_작은_쪽이다() {
        assertEquals(2_000, DiscountAllocator.maxUsablePoints(59_000, 2_000, policy))
        assertEquals(17_700, DiscountAllocator.maxUsablePoints(59_000, 20_000, policy))
        assertEquals(2_340, DiscountAllocator.maxUsablePoints(59_000, 2_345, policy))
    }
}
