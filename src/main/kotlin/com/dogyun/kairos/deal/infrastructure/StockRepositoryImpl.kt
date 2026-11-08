package com.dogyun.kairos.deal.infrastructure

import com.dogyun.kairos.deal.domain.DealId
import com.dogyun.kairos.deal.domain.Stock
import com.dogyun.kairos.deal.domain.StockRepository
import org.springframework.stereotype.Repository

@Repository
class StockRepositoryImpl(private val jpaRepository: StockJpaRepository) : StockRepository {

    override fun save(stock: Stock): Stock {
        val entity = jpaRepository.findByDealId(stock.dealId.value)
            ?.also { it.change(stock.total, stock.remaining) }
            ?: StockEntity(stock.dealId.value, stock.total, stock.remaining)
        return toDomain(jpaRepository.saveAndFlush(entity))
    }

    override fun findByDealId(dealId: DealId): Stock? = jpaRepository.findByDealId(dealId.value)?.let(::toDomain)

    override fun deleteByDealId(dealId: DealId) {
        jpaRepository.findByDealId(dealId.value)?.let {
            it.softDelete()
            jpaRepository.saveAndFlush(it)
        }
    }

    private fun toDomain(entity: StockEntity) = Stock(DealId(entity.dealId), entity.totalQuantity, entity.remainingQuantity)
}
