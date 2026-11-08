package com.dogyun.kairos.deal.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.deal.domain.Deal
import com.dogyun.kairos.deal.domain.DealId
import com.dogyun.kairos.deal.domain.DealRepository
import com.dogyun.kairos.deal.domain.DealStatus
import com.dogyun.kairos.deal.domain.StockRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class DealQueryService(
    private val dealRepository: DealRepository,
    private val stockRepository: StockRepository,
    private val clock: Clock,
) {
    fun getSnapshot(dealId: Long): DealSnapshot {
        val deal = find(dealId)
        return DealSnapshot(
            dealId = dealId,
            dealPrice = deal.dealPrice.amount,
            perMemberLimit = deal.perMemberLimit,
            ongoing = deal.statusAt(LocalDateTime.now(clock)) == DealStatus.ONGOING,
        )
    }

    fun list(status: DealStatus?, page: Int, size: Int): PageResult<DealSummary> {
        if (page < 0 || size !in 1..MAX_SIZE) throw DomainException(ErrorCode.INVALID_REQUEST)
        val now = LocalDateTime.now(clock)
        val result = dealRepository.findPage(status, now, page, size)
        return PageResult(
            content = result.content.map { summary(it, now) },
            page = result.page,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext,
        )
    }

    fun detail(dealId: Long): DealDetail {
        val deal = find(dealId)
        val stock = stockRepository.findByDealId(DealId(dealId)) ?: throw DomainException(ErrorCode.DEAL_NOT_FOUND)
        val s = summary(deal, LocalDateTime.now(clock))
        return DealDetail(
            dealId = s.dealId,
            productName = s.productName,
            originalPrice = s.originalPrice,
            dealPrice = s.dealPrice,
            openAt = s.openAt,
            closeAt = s.closeAt,
            status = s.status,
            totalQuantity = deal.totalQuantity,
            perMemberLimit = deal.perMemberLimit,
            remainingQuantity = stock.remaining,
        )
    }

    private fun find(dealId: Long): Deal =
        dealRepository.findById(DealId(dealId)) ?: throw DomainException(ErrorCode.DEAL_NOT_FOUND)

    private fun summary(deal: Deal, now: LocalDateTime) = DealSummary(
        dealId = requireNotNull(deal.id).value,
        productName = deal.productName,
        originalPrice = deal.originalPrice.amount,
        dealPrice = deal.dealPrice.amount,
        openAt = deal.openAt,
        closeAt = deal.closeAt,
        status = deal.statusAt(now),
    )

    private companion object {
        const val MAX_SIZE = 100
    }
}
