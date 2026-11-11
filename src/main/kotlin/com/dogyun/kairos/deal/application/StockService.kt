package com.dogyun.kairos.deal.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.deal.domain.DealId
import com.dogyun.kairos.deal.domain.StockRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

/**
 * Phase 1: 락 없이 읽기 -> 도메인 검사 -> 저장. 동시 요청 시 초과 판매(lost update)가 가능하며
 * Phase 2에서 재현·측정하고 Phase 3에서 개선한다. 여기에 락을 넣지 않는다.
 */
@Service
class StockService(private val stockRepository: StockRepository) {

    @Transactional
    fun decrease(dealId: Long, quantity: Int) {
        val stock = find(dealId)
        stock.decrease(quantity)
        stockRepository.save(stock)
    }

    @Transactional
    fun restore(dealId: Long, quantity: Int) {
        val stock = find(dealId)
        stock.restore(quantity)
        stockRepository.save(stock)
        if (stock.remaining > stock.total) {
            log.warn { "oversell detected: dealId=$dealId, remaining=${stock.remaining}, total=${stock.total}" }
        }
    }

    private fun find(dealId: Long) =
        stockRepository.findByDealId(DealId(dealId)) ?: throw DomainException(ErrorCode.DEAL_NOT_FOUND)
}
