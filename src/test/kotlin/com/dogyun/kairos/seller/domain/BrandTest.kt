package com.dogyun.kairos.seller.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BrandTest {

    private fun pending() = Brand.apply(1L, "키로스", "Kairos", null, null)

    @Test
    fun 신청하면_PENDING() {
        val brand = pending()

        assertEquals(BrandStatus.PENDING, brand.status)
        assertNull(brand.rejectReason)
    }

    @Test
    fun 승인_후_반려는_INVALID_STATE_TRANSITION() {
        val brand = pending().apply { approve() }

        val e = assertFailsWith<DomainException> { brand.reject("사유") }

        assertEquals(ErrorCode.BRAND_INVALID_STATE_TRANSITION, e.errorCode)
    }

    @Test
    fun 반려된_브랜드만_재신청할_수_있다() {
        val pending = pending()
        val e = assertFailsWith<DomainException> { pending.reapply("새이름", null, null, null) }
        assertEquals(ErrorCode.BRAND_INVALID_STATE_TRANSITION, e.errorCode)

        val rejected = pending().apply { reject("로고 불명확") }
        rejected.reapply("새이름", "New", 5L, "설명")

        assertEquals(BrandStatus.PENDING, rejected.status)
        assertEquals("새이름", rejected.name)
        assertEquals(5L, rejected.logoFileId)
        assertNull(rejected.rejectReason)
    }

    @Test
    fun 이름이_비면_BRAND_INVALID() {
        listOf("", "  ", "가".repeat(51)).forEach {
            val e = assertFailsWith<DomainException> { Brand.apply(1L, it, null, null, null) }
            assertEquals(ErrorCode.BRAND_INVALID, e.errorCode)
        }
    }
}
