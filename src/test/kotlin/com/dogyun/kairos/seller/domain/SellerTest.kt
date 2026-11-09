package com.dogyun.kairos.seller.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SellerTest {

    private fun apply() = Seller.apply(
        memberId = 1L,
        companyName = "키로스상사",
        businessNumber = BusinessNumber("123-45-67890"),
        representativeName = "홍길동",
        phone = "010-1234-5678",
        settlementAccount = SettlementAccount("국민", "123456-01-123456", "홍길동"),
    )

    private fun assertCode(code: ErrorCode, block: () -> Unit) {
        assertEquals(code, assertFailsWith<DomainException> { block() }.errorCode)
    }

    @Test
    fun 신청하면_PENDING이고_수수료율은_0이다() {
        val seller = apply()

        assertEquals(SellerStatus.PENDING, seller.status)
        assertEquals(0, seller.commissionRateBp)
        assertNull(seller.id)
    }

    @Test
    fun 사업자등록번호_형식이_틀리면_SELLER_INVALID() {
        assertCode(ErrorCode.SELLER_INVALID) { BusinessNumber("1234567890") }
        assertCode(ErrorCode.SELLER_INVALID) { BusinessNumber("123-45-6789a") }
    }

    @Test
    fun PENDING에서_승인하면_APPROVED_수수료율_저장() {
        val seller = apply()

        seller.approve(1000)

        assertEquals(SellerStatus.APPROVED, seller.status)
        assertEquals(1000, seller.commissionRateBp)
    }

    @Test
    fun 승인된_판매자를_다시_승인하면_INVALID_STATE_TRANSITION() {
        val seller = apply().also { it.approve(1000) }

        assertCode(ErrorCode.SELLER_INVALID_STATE_TRANSITION) { seller.approve(500) }
    }

    @Test
    fun 반려된_판매자가_신청을_고치면_PENDING으로_돌아가고_사유가_지워진다() {
        val seller = apply().also { it.reject("서류 미비") }
        assertEquals("서류 미비", seller.rejectReason)

        seller.updateApplication(
            "새상사", BusinessNumber("111-22-33333"), "김철수", "010-0000-0000",
            SettlementAccount("신한", "110-1", "김철수"),
        )

        assertEquals(SellerStatus.PENDING, seller.status)
        assertNull(seller.rejectReason)
        assertEquals("새상사", seller.companyName)
        assertEquals(BusinessNumber("111-22-33333"), seller.businessNumber)
    }

    @Test
    fun APPROVED에서_정지_SUSPENDED에서_해제() {
        val seller = apply().also { it.approve(500) }

        seller.suspend("정책 위반")
        assertEquals(SellerStatus.SUSPENDED, seller.status)
        assertEquals("정책 위반", seller.suspendReason)

        seller.unsuspend()
        assertEquals(SellerStatus.APPROVED, seller.status)
        assertNull(seller.suspendReason)
    }

    @Test
    fun PENDING에서_정지는_INVALID_STATE_TRANSITION() {
        assertCode(ErrorCode.SELLER_INVALID_STATE_TRANSITION) { apply().suspend("사유") }
    }

    @Test
    fun 수수료율_10001은_SELLER_INVALID() {
        assertCode(ErrorCode.SELLER_INVALID) { apply().approve(10001) }
        assertCode(ErrorCode.SELLER_INVALID) { apply().approve(-1) }
    }

    @Test
    fun canManage는_APPROVED만_canView는_APPROVED와_SUSPENDED() {
        val pending = apply()
        assertFalse(pending.canManage)
        assertFalse(pending.canView)

        val approved = apply().also { it.approve(0) }
        assertTrue(approved.canManage)
        assertTrue(approved.canView)

        val suspended = apply().also { it.approve(0); it.suspend("x") }
        assertFalse(suspended.canManage)
        assertTrue(suspended.canView)

        val rejected = apply().also { it.reject("x") }
        assertFalse(rejected.canManage)
        assertFalse(rejected.canView)
    }
}
