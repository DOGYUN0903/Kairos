package com.dogyun.kairos.deal.domain

interface StockRepository {
    fun save(stock: Stock): Stock
    fun findByDealId(dealId: DealId): Stock?
}
