package com.dogyun.kairos.settlement.domain

import com.dogyun.kairos.common.domain.PageResult
import java.time.LocalDate

interface SettlementRepository {
    fun save(s: Settlement): Settlement
    fun findById(id: Long): Settlement?
    fun existsBySellerIdAndPeriodStart(sellerId: Long, periodStart: LocalDate): Boolean
    fun findSettledSellerOrderIds(sellerOrderIds: Collection<Long>): Set<Long>
    fun findPageBySellerId(sellerId: Long, page: Int, size: Int): PageResult<Settlement>
    fun findPageForAdmin(sellerId: Long?, status: SettlementStatus?, page: Int, size: Int): PageResult<Settlement>
    fun findLines(settlementId: Long): List<SettlementLine>
}
