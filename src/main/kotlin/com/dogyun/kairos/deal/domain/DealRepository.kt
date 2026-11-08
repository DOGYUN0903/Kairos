package com.dogyun.kairos.deal.domain

interface DealRepository {
    fun save(deal: Deal): Deal
    fun findById(id: DealId): Deal?
    fun findAll(): List<Deal>
    fun delete(id: DealId)
}
