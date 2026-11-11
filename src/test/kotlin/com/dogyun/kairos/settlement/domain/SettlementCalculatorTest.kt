package com.dogyun.kairos.settlement.domain

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class SettlementCalculatorTest {
    private val at = LocalDateTime.of(2026, 11, 17, 10, 0)

    private fun item(id: Long, ordered: Boolean = true, amount: Long, coupon: Long = 0, issuer: String? = null, fee: Long = 0) =
        SettlementSourceItem(id, ordered, amount, coupon, issuer, fee)

    private fun source(
        id: Long = 1, rateBp: Int = 1000, shipping: Long = 3000, refunded: Long = 0, items: List<SettlementSourceItem>,
    ) = SettlementSource(id, 7, 100 + id, "K$id", at, rateBp, shipping, refunded, items)

    @Test
    fun 라인1_판매자_쿠폰_5000_장바구니_8000_포인트_2000_무료배송은_70500이다() {
        val c = SettlementCalculator.line(
            source(items = listOf(item(1, amount = 50_000, coupon = 5_000, issuer = "SELLER"), item(2, amount = 30_000))),
        )

        assertEquals(80_000, c.salesAmount)
        assertEquals(5_000, c.sellerCouponDiscount)
        assertEquals(75_000, c.commissionBase)
        assertEquals(7_500, c.commissionAmount)
        assertEquals(3_000, c.shippingFeeOwed)
        assertEquals(0, c.returnFeeBorne)
        assertEquals(70_500, c.lineAmount)
    }

    @Test
    fun 라인2_불량_반품_항목_제외_15000_return_borne_3000은_13500이다() {
        val c = SettlementCalculator.line(
            source(id = 2, items = listOf(item(3, ordered = false, amount = 20_000, fee = 3_000), item(4, amount = 15_000))),
        )

        assertEquals(15_000, c.salesAmount)
        assertEquals(1_500, c.commissionAmount)
        assertEquals(3_000, c.shippingFeeOwed)
        assertEquals(3_000, c.returnFeeBorne)
        assertEquals(13_500, c.lineAmount)
    }

    @Test
    fun 라인3_전부_반품_sales_0_shipping_0_return_borne_3000은_minus_3000이다() {
        val c = SettlementCalculator.line(
            source(id = 3, refunded = 3_000, items = listOf(item(5, ordered = false, amount = 10_000, fee = 3_000))),
        )

        assertEquals(0, c.salesAmount)
        assertEquals(0, c.shippingFeeOwed)
        assertEquals(3_000, c.returnFeeBorne)
        assertEquals(-3_000, c.lineAmount)
    }

    @Test
    fun 버림_12345_1000bp는_1234_9는_0_850bp는_1049다() {
        assertEquals(1_234, SettlementCalculator.commission(12_345, 1000))
        assertEquals(0, SettlementCalculator.commission(9, 1000))
        assertEquals(1_049, SettlementCalculator.commission(12_345, 850))
    }

    @Test
    fun `800bp_판매자_주문은_8퍼센트다`() {
        val c = SettlementCalculator.line(source(id = 4, rateBp = 800, items = listOf(item(6, amount = 10_000))))

        assertEquals(800, c.commissionAmount)
        assertEquals(800, c.commissionRateBp)
    }

    @Test
    fun 플랫폼_쿠폰_포인트_무료배송을_바꿔도_line_amount가_같다() {
        // 플랫폼 부담분은 SettlementSource에 들어오지 않는다. 플랫폼 쿠폰 발급 항목은 판매자 쿠폰이 아니므로 빼지 않는다.
        val withoutPlatform = SettlementCalculator.line(source(items = listOf(item(1, amount = 50_000))))
        val withPlatformCoupon = SettlementCalculator.line(source(items = listOf(item(1, amount = 50_000, coupon = 4_000, issuer = "PLATFORM"))))

        assertEquals(withoutPlatform.lineAmount, withPlatformCoupon.lineAmount)
        assertEquals(0, withPlatformCoupon.sellerCouponDiscount)
    }

    @Test
    fun 같은_입력을_두_번_계산하면_같다() {
        val s = source(items = listOf(item(1, amount = 12_345, coupon = 345, issuer = "SELLER"), item(2, ordered = false, amount = 9, fee = 100)))

        assertEquals(SettlementCalculator.line(s), SettlementCalculator.line(s))
    }
}
