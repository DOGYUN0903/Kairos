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

    @Test
    fun 항목_금액은_판매가_더하기_옵션가_곱하기_수량이다() {
        val a = DiscountAllocator.allocate(
            listOf(line(1, 10_000, quantity = 2, skuId = 11, extra = 1_500)), emptyMap(),
            cartCoupon(type = CouponDiscountType.RATE, value = 10_000), 0, 0, policy,
        )
        assertEquals(23_000, a.cartCouponDiscount) // (10,000 + 1,500) × 2, 100% 할인
    }

    @Test
    fun 배분_나머지는_판매자_다음_SKU_순서의_마지막_항목에_얹는다() {
        // 판매자 순서(1 < 2)와 SKU 순서(99 > 5)가 반대다. 정렬 기준은 판매자가 먼저다.
        val bySeller = DiscountAllocator.allocate(
            listOf(line(1, 10_000, skuId = 99), line(2, 10_000, skuId = 5)), emptyMap(),
            cartCoupon(type = CouponDiscountType.FIXED, value = 10_001), 0, 0, policy,
        )
        assertEquals(5_000, bySeller.lines.getValue(99).cartCouponAllocated)
        assertEquals(5_001, bySeller.lines.getValue(5).cartCouponAllocated)

        // 같은 판매자 안에서는 SKU 오름차순이라 SKU 가 큰 항목이 마지막이다.
        val bySku = DiscountAllocator.allocate(
            listOf(line(1, 10_000, skuId = 12), line(1, 10_000, skuId = 11)), emptyMap(),
            cartCoupon(type = CouponDiscountType.FIXED, value = 10_001), 0, 0, policy,
        )
        assertEquals(5_000, bySku.lines.getValue(11).cartCouponAllocated)
        assertEquals(5_001, bySku.lines.getValue(12).cartCouponAllocated)
    }

    @Test
    fun 마지막_항목_잔액보다_나머지가_크면_앞쪽_항목으로_넘긴다() {
        // 5 / 5 / 1원에 10원 할인: 버림 4, 4, 0 → 나머지 2 중 1은 마지막(여유 1), 1은 가운데로.
        val a = DiscountAllocator.allocate(
            listOf(line(1, 5, skuId = 11), line(2, 5, skuId = 21), line(3, 1, skuId = 31)), emptyMap(),
            cartCoupon(type = CouponDiscountType.FIXED, value = 10), 0, 0, policy,
        )
        assertEquals(listOf(4L, 5L, 1L), listOf(11L, 21L, 31L).map { a.lines.getValue(it).cartCouponAllocated })
    }

    @Test
    fun 정률_장바구니_할인이_0원이면_모든_항목_배분이_0이다() {
        val a = DiscountAllocator.allocate(
            listOf(line(1, 5_000, skuId = 11), line(2, 4_000, skuId = 21)), emptyMap(),
            cartCoupon(type = CouponDiscountType.RATE, value = 1), 0, 0, policy, // 9,000 × 0.01% = 0.9 → 0
        )
        assertEquals(0, a.cartCouponDiscount)
        assertEquals(0, a.lines.getValue(11).cartCouponAllocated)
        assertEquals(0, a.lines.getValue(21).cartCouponAllocated)
    }

    @Test
    fun 잔액이_정확히_1000P이고_전부_쓰면_허용한다() {
        val a = DiscountAllocator.allocate(listOf(b1), emptyMap(), null, 1_000, 1_000, policy)
        assertEquals(1_000, a.pointsUsed)
        assertEquals(1_000, a.lines.getValue(21).pointsAllocated)
        assertEquals(1_000, DiscountAllocator.maxUsablePoints(30_000, 1_000, policy))
        assertEquals(0, DiscountAllocator.maxUsablePoints(-100, 5_000, policy))
    }

    @Test
    fun 쿠폰_대상이_맞으면_판매자_쿠폰과_상품_쿠폰을_적용한다() {
        val bySeller = productCoupon(1, CouponIssuer.SELLER, CouponDiscountType.FIXED, 1_000, target = CouponTarget.SELLER, sellerId = 2)
        assertEquals(1_000, DiscountAllocator.allocate(listOf(b1), mapOf(21L to bySeller), null, 0, 0, policy).productCouponDiscount)
        val byProduct = productCoupon(2, CouponIssuer.SELLER, CouponDiscountType.FIXED, 700, target = CouponTarget.PRODUCT, productId = 1021)
        assertEquals(700, DiscountAllocator.allocate(listOf(b1), mapOf(21L to byProduct), null, 0, 0, policy).productCouponDiscount)
        assertEquals(false, bySeller.appliesTo(productId = 1021, sellerId = 3))
        assertEquals(false, byProduct.appliesTo(productId = 1022, sellerId = 2))
    }

    @Test
    fun 대상_금액이_0_이하면_할인은_0원이다() {
        val fixed = productCoupon(1, CouponIssuer.PLATFORM, CouponDiscountType.FIXED, 1_000)
        assertEquals(0, fixed.discountFor(0))
        assertEquals(0, fixed.discountFor(-1))
        assertEquals(500, fixed.discountFor(500))
        val rate = productCoupon(2, CouponIssuer.PLATFORM, CouponDiscountType.RATE, 1_000)
        assertEquals(0, rate.discountFor(-10_000))
    }

    @Test
    fun 앞쪽으로_넘기는_나머지는_각_항목의_남은_여유까지만_얹는다() {
        // 5 / 5 / 3원에 12원 할인: 버림 4, 4, 2 → 나머지 2 중 마지막은 여유 1만, 남은 1은 가운데로.
        val a = DiscountAllocator.allocate(
            listOf(line(1, 5, skuId = 11), line(2, 5, skuId = 21), line(3, 3, skuId = 31)), emptyMap(),
            cartCoupon(type = CouponDiscountType.FIXED, value = 12), 0, 0, policy,
        )
        assertEquals(listOf(4L, 5L, 3L), listOf(11L, 21L, 31L).map { a.lines.getValue(it).cartCouponAllocated })
    }

    @Test
    fun 대상_ID가_비어_있는_판매자_상품_쿠폰은_어디에도_적용되지_않는다() {
        val noSeller = productCoupon(1, CouponIssuer.SELLER, CouponDiscountType.FIXED, 1_000, target = CouponTarget.SELLER)
        val noProduct = productCoupon(2, CouponIssuer.SELLER, CouponDiscountType.FIXED, 1_000, target = CouponTarget.PRODUCT)
        assertEquals(false, noSeller.appliesTo(productId = 1021, sellerId = 2))
        assertEquals(false, noProduct.appliesTo(productId = 1021, sellerId = 2))
    }

    @Test
    fun 잔액이_최소_사용_잔액_미만이면_요청_포인트가_잔액보다_커도_POINT_EXCEEDS_LIMIT() {
        // 잔액 부족(POINT_INSUFFICIENT)보다 최소 잔액 미달이 먼저 판정된다.
        assertCode(ErrorCode.POINT_EXCEEDS_LIMIT) { DiscountAllocator.allocate(listOf(b1), emptyMap(), null, 5_000, 999, policy) }
    }

    @Test
    fun 쿠폰_적용_후_금액이_0이면_요청_포인트가_잔액보다_커도_POINT_EXCEEDS_LIMIT() {
        val full = productCoupon(1, CouponIssuer.PLATFORM, CouponDiscountType.FIXED, 30_000)
        assertCode(ErrorCode.POINT_EXCEEDS_LIMIT) {
            DiscountAllocator.allocate(listOf(b1), mapOf(21L to full), null, 5_000, 2_000, policy)
        }
    }
}
