package com.dogyun.kairos.seller.application

import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.seller.domain.BusinessNumber
import com.dogyun.kairos.seller.domain.Seller
import com.dogyun.kairos.seller.domain.SellerId
import com.dogyun.kairos.seller.domain.SellerRepository
import com.dogyun.kairos.seller.domain.SellerStatus
import com.dogyun.kairos.seller.domain.SettlementAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SellerQueryServiceTest {

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
        override fun existsByBusinessNumber(businessNumber: BusinessNumber): Boolean = false
        override fun findPage(status: SellerStatus?, page: Int, size: Int): PageResult<Seller> =
            PageResult.of(emptyList(), page, size, 0)
    }

    private val repository = FakeSellerRepository()
    private val queryService = SellerQueryService(repository)

    @Test
    fun 정산_스냅샷은_회사명과_수수료율을_담는다() {
        val seller = Seller.apply(
            1L, "키로스상사", BusinessNumber("123-45-67890"), "홍길동", "010-1234-5678",
            SettlementAccount("국민", "123456-01-123456", "홍길동"),
        )
        seller.approve(1000)
        val id = requireNotNull(repository.save(seller).id).value

        val snapshots = queryService.findSettlementSnapshots(listOf(id, 999L))

        assertEquals(setOf(id), snapshots.keys)
        assertEquals(SellerSettlementSnapshot(id, "키로스상사", 1000, canManage = true, sellerStatus = "APPROVED"), snapshots.getValue(id))
        assertTrue(snapshots.getValue(id).canManage)
    }
}
