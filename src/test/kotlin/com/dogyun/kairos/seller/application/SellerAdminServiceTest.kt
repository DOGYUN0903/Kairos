package com.dogyun.kairos.seller.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.seller.domain.BusinessNumber
import com.dogyun.kairos.seller.domain.ReviewHistory
import com.dogyun.kairos.seller.domain.ReviewHistoryRepository
import com.dogyun.kairos.seller.domain.ReviewResult
import com.dogyun.kairos.seller.domain.ReviewTarget
import com.dogyun.kairos.seller.domain.Seller
import com.dogyun.kairos.seller.domain.SellerId
import com.dogyun.kairos.seller.domain.SellerRepository
import com.dogyun.kairos.seller.domain.SellerStatus
import com.dogyun.kairos.seller.domain.SettlementAccount
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SellerAdminServiceTest {

    private class FakeSellerRepository : SellerRepository {
        private val store = linkedMapOf<Long, Seller>()
        private var sequence = 0L

        override fun save(seller: Seller): Seller {
            val id = seller.id ?: SellerId(++sequence)
            val saved = Seller.restore(
                id, seller.memberId, seller.companyName, seller.businessNumber, seller.representativeName,
                seller.phone, seller.settlementAccount, seller.commissionRateBp, seller.status,
                seller.rejectReason, seller.suspendReason,
            )
            store[id.value] = saved
            return saved
        }

        override fun findById(id: SellerId): Seller? = store[id.value]
        override fun findAllByIds(ids: Collection<SellerId>): List<Seller> = ids.mapNotNull { store[it.value] }
        override fun findByMemberId(memberId: Long): Seller? = store.values.firstOrNull { it.memberId == memberId }
        override fun existsByBusinessNumber(businessNumber: BusinessNumber): Boolean =
            store.values.any { it.businessNumber == businessNumber }

        override fun findPage(status: SellerStatus?, page: Int, size: Int): PageResult<Seller> {
            val all = store.values.filter { status == null || it.status == status }
            return PageResult.of(all.drop(page * size).take(size), page, size, all.size.toLong())
        }
    }

    private class FakeReviewHistoryRepository : ReviewHistoryRepository {
        val store = mutableListOf<ReviewHistory>()

        override fun save(history: ReviewHistory): ReviewHistory {
            val saved = ReviewHistory(
                store.size + 1L, history.target, history.targetId, history.result,
                history.reason, history.reviewerId, history.reviewedAt,
            )
            store += saved
            return saved
        }

        override fun findAllByTarget(target: ReviewTarget, targetId: Long): List<ReviewHistory> =
            store.filter { it.target == target && it.targetId == targetId }
    }

    private val now = LocalDateTime.of(2026, 11, 9, 10, 0)
    private val clock = Clock.fixed(now.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"))
    private val sellers = FakeSellerRepository()
    private val histories = FakeReviewHistoryRepository()
    private val service = SellerAdminService(sellers, histories, clock)

    private fun pendingSeller(): Long = requireNotNull(
        sellers.save(
            Seller.apply(
                1L, "키로스상사", BusinessNumber("123-45-67890"), "홍길동", "010-1234-5678",
                SettlementAccount("국민", "123456-01-123456", "홍길동"),
            ),
        ).id,
    ).value

    private fun current(id: Long) = requireNotNull(sellers.findById(SellerId(id)))

    @Test
    fun 승인하면_APPROVED가_되고_이력이_남는다() {
        val id = pendingSeller()

        service.approve(id, 1000, 99L)

        assertEquals(SellerStatus.APPROVED, current(id).status)
        assertEquals(1000, current(id).commissionRateBp)
        val history = histories.findAllByTarget(ReviewTarget.SELLER, id).single()
        assertEquals(ReviewResult.APPROVED, history.result)
        assertEquals(99L, history.reviewerId)
        assertEquals(now, history.reviewedAt)
        assertNull(history.reason)
    }

    @Test
    fun 반려_사유가_판매자와_이력에_남는다() {
        val id = pendingSeller()

        service.reject(id, "서류 미비", 99L)

        assertEquals(SellerStatus.REJECTED, current(id).status)
        assertEquals("서류 미비", current(id).rejectReason)
        val history = histories.findAllByTarget(ReviewTarget.SELLER, id).single()
        assertEquals(ReviewResult.REJECTED, history.result)
        assertEquals("서류 미비", history.reason)
    }

    @Test
    fun 정지_후_해제하면_APPROVED이고_이력이_두_건이다() {
        val id = pendingSeller()
        service.approve(id, 500, 99L)

        service.suspend(id, "약관 위반", 99L)
        assertEquals(SellerStatus.SUSPENDED, current(id).status)
        service.unsuspend(id, 99L)

        assertEquals(SellerStatus.APPROVED, current(id).status)
        val results = histories.findAllByTarget(ReviewTarget.SELLER, id).map { it.result }
        assertEquals(listOf(ReviewResult.APPROVED, ReviewResult.SUSPENDED, ReviewResult.UNSUSPENDED), results)
    }

    @Test
    fun 없는_판매자_승인은_SELLER_NOT_FOUND() {
        val e = assertFailsWith<DomainException> { service.approve(404L, 1000, 99L) }

        assertEquals(ErrorCode.SELLER_NOT_FOUND, e.errorCode)
    }

    @Test
    fun 허용되지_않는_전이는_이력을_남기지_않는다() {
        val id = pendingSeller()
        service.approve(id, 1000, 99L)

        val e = assertFailsWith<DomainException> { service.approve(id, 1000, 99L) }

        assertEquals(ErrorCode.SELLER_INVALID_STATE_TRANSITION, e.errorCode)
        assertEquals(1, histories.store.size)
    }
}
