package com.dogyun.kairos.seller.domain

interface ReviewHistoryRepository {
    fun save(history: ReviewHistory): ReviewHistory
    fun findAllByTarget(target: ReviewTarget, targetId: Long): List<ReviewHistory>
}
