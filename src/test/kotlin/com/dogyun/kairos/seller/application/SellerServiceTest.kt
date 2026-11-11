package com.dogyun.kairos.seller.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.ListOrder
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.seller.domain.BusinessNumber
import com.dogyun.kairos.seller.domain.Seller
import com.dogyun.kairos.seller.domain.SellerId
import com.dogyun.kairos.seller.domain.SellerRepository
import com.dogyun.kairos.seller.domain.SellerStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SellerServiceTest {

    private class FakeSellerRepository : SellerRepository {
        private val store = linkedMapOf<Long, Seller>()
        private var sequence = 0L

        override fun save(seller: Seller): Seller {
            val id = seller.id ?: SellerId(++sequence)
            val saved = Seller.restore(
                id, seller.memberId, seller.companyName, seller.businessNumber, seller.representativeName,
                seller.phone, seller.settlementAccount, seller.commissionRateBp, seller.status,
                seller.rejectReason, seller.suspendReason, createdAt = java.time.LocalDateTime.of(2026, 11, 9, 9, 0),
            )
            store[id.value] = saved
            return saved
        }

        override fun findById(id: SellerId): Seller? = store[id.value]
        override fun findAllByIds(ids: Collection<SellerId>): List<Seller> = ids.mapNotNull { store[it.value] }
        override fun findByMemberId(memberId: Long): Seller? = store.values.firstOrNull { it.memberId == memberId }
        override fun existsByBusinessNumber(businessNumber: BusinessNumber): Boolean =
            store.values.any { it.businessNumber == businessNumber }

        override fun findPage(status: SellerStatus?, order: ListOrder, page: Int, size: Int): PageResult<Seller> {
            val all = store.values.filter { status == null || it.status == status }.sortedByDescending { it.id?.value }
            return PageResult.of(all.drop(page * size).take(size), page, size, all.size.toLong())
        }
    }

    private val repository = FakeSellerRepository()
    private val service = SellerService(repository)
    private val queryService = SellerQueryService(repository)

    private fun command(businessNumber: String = "123-45-67890", company: String = "키로스상사") = SellerApplicationCommand(
        companyName = company,
        businessNumber = businessNumber,
        representativeName = "홍길동",
        phone = "010-1234-5678",
        bank = "국민",
        accountNumber = "123456-01-123456",
        holder = "홍길동",
    )

    private fun assertCode(code: ErrorCode, block: () -> Unit) {
        assertEquals(code, assertFailsWith<DomainException> { block() }.errorCode)
    }

    @Test
    fun 입점_신청하면_PENDING_판매자가_저장된다() {
        val sellerId = service.apply(1L, command())

        val detail = queryService.getMine(1L)
        assertEquals(sellerId, detail.sellerId)
        assertEquals("PENDING", detail.status)
        assertEquals(0, detail.commissionRateBp)
        assertEquals("123-45-67890", detail.businessNumber)
    }

    @Test
    fun 이미_판매자인_회원의_신청은_SELLER_ALREADY_EXISTS() {
        service.apply(1L, command())

        assertCode(ErrorCode.SELLER_ALREADY_EXISTS) { service.apply(1L, command("111-22-33333")) }
    }

    @Test
    fun 사업자등록번호가_중복되면_SELLER_BUSINESS_NUMBER_DUPLICATED() {
        service.apply(1L, command())

        assertCode(ErrorCode.SELLER_BUSINESS_NUMBER_DUPLICATED) { service.apply(2L, command()) }
    }

    @Test
    fun 다른_판매자의_사업자등록번호로_신청을_고치면_SELLER_BUSINESS_NUMBER_DUPLICATED() {
        service.apply(1L, command())
        service.apply(2L, command("111-22-33333"))

        assertCode(ErrorCode.SELLER_BUSINESS_NUMBER_DUPLICATED) { service.updateApplication(2L, command("123-45-67890")) }
    }

    @Test
    fun 판매자가_아니면_신청_수정은_SELLER_NOT_FOUND() {
        assertCode(ErrorCode.SELLER_NOT_FOUND) { service.updateApplication(1L, command()) }
        assertCode(ErrorCode.SELLER_NOT_FOUND) { queryService.getMine(1L) }
    }

    @Test
    fun PENDING_신청은_고칠_수_있다() {
        service.apply(1L, command())

        service.updateApplication(1L, command(company = "새상사"))

        assertEquals("새상사", queryService.getMine(1L).companyName)
    }

    @Test
    fun 승인된_판매자가_신청을_고치면_INVALID_STATE_TRANSITION() {
        service.apply(1L, command())
        repository.findByMemberId(1L)!!.also { it.approve(1000); repository.save(it) }

        assertCode(ErrorCode.SELLER_INVALID_STATE_TRANSITION) { service.updateApplication(1L, command(company = "새상사")) }
    }

    @Test
    fun 스냅샷은_상태를_canManage_canView로_번역한다() {
        fun snapshotOf(memberId: Long, transition: (Seller) -> Unit): SellerSnapshot {
            service.apply(memberId, command("100-00-0000$memberId"))
            repository.findByMemberId(memberId)!!.also { transition(it); repository.save(it) }
            return assertNotNull(queryService.findSnapshotByMemberId(memberId))
        }

        val approved = snapshotOf(1L) { it.approve(0) }
        assertEquals(true, approved.canManage)
        assertEquals(true, approved.canView)

        val suspended = snapshotOf(2L) { it.approve(0); it.suspend("x") }
        assertEquals(false, suspended.canManage)
        assertEquals(true, suspended.canView)

        val pending = snapshotOf(3L) { }
        assertEquals(false, pending.canManage)
        assertEquals(false, pending.canView)

        assertNull(queryService.findSnapshotByMemberId(999L))
    }

    @Test
    fun 관리자_목록은_상태로_거르고_최신순이다() {
        service.apply(1L, command("100-00-00001"))
        service.apply(2L, command("100-00-00002"))
        repository.findByMemberId(1L)!!.also { it.approve(0); repository.save(it) }

        val all = queryService.listForAdmin(null, ListOrder.LATEST, 0, 20)
        assertEquals(listOf(2L, 1L), all.content.map { it.memberId })
        assertEquals(listOf(1L), queryService.listForAdmin(SellerStatus.APPROVED, ListOrder.LATEST, 0, 20).content.map { it.memberId })
        assertCode(ErrorCode.INVALID_REQUEST) { queryService.listForAdmin(null, ListOrder.LATEST, 0, 101) }
    }
}
