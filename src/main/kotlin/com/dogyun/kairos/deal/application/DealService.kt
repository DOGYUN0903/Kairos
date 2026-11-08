package com.dogyun.kairos.deal.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.Money
import com.dogyun.kairos.deal.domain.Deal
import com.dogyun.kairos.deal.domain.DealId
import com.dogyun.kairos.deal.domain.DealRepository
import com.dogyun.kairos.deal.domain.Stock
import com.dogyun.kairos.deal.domain.StockRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class DealService(
    private val dealRepository: DealRepository,
    private val stockRepository: StockRepository,
    private val clock: Clock,
) {
    // 의도적 예외(설계 9.4): Deal 과 Stock 두 애그리거트를 한 트랜잭션에서 함께 저장한다.
    @Transactional
    fun create(command: CreateDealCommand): Long {
        val deal = dealRepository.save(
            Deal(
                id = null,
                productName = command.productName,
                originalPrice = money(command.originalPrice),
                dealPrice = money(command.dealPrice),
                totalQuantity = command.totalQuantity,
                perMemberLimit = command.perMemberLimit,
                openAt = command.openAt,
                closeAt = command.closeAt,
            ),
        )
        val dealId = requireNotNull(deal.id)
        stockRepository.save(Stock(dealId, deal.totalQuantity, deal.totalQuantity))
        return dealId.value
    }

    /** 오픈 전(판매 이력 없음)에만 가능하므로 재고는 새 총수량으로 다시 채운다. */
    // 의도적 예외(설계 9.4): Deal 과 Stock 두 애그리거트를 한 트랜잭션에서 함께 저장한다.
    @Transactional
    fun update(dealId: Long, command: UpdateDealCommand) {
        val deal = dealRepository.findById(DealId(dealId)) ?: throw DomainException(ErrorCode.DEAL_NOT_FOUND)
        deal.update(
            productName = command.productName,
            originalPrice = money(command.originalPrice),
            dealPrice = money(command.dealPrice),
            totalQuantity = command.totalQuantity,
            perMemberLimit = command.perMemberLimit,
            openAt = command.openAt,
            closeAt = command.closeAt,
            now = LocalDateTime.now(clock),
        )
        dealRepository.save(deal)
        stockRepository.save(Stock(DealId(dealId), deal.totalQuantity, deal.totalQuantity))
    }

    @Transactional
    fun delete(dealId: Long) {
        val deal = dealRepository.findById(DealId(dealId)) ?: throw DomainException(ErrorCode.DEAL_NOT_FOUND)
        deal.ensureDeletable(LocalDateTime.now(clock))
        dealRepository.delete(DealId(dealId))
        stockRepository.deleteByDealId(DealId(dealId))
    }

    // 음수 금액은 Money 의 require 대신 DEAL_INVALID 로 응답한다.
    private fun money(amount: Long): Money =
        if (amount < 0) throw DomainException(ErrorCode.DEAL_INVALID) else Money(amount)
}
