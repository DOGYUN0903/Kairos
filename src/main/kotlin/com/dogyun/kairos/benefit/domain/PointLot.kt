package com.dogyun.kairos.benefit.domain

import java.time.LocalDateTime

/** 적립 로트. 사용은 만료가 가까운 로트부터(FIFO) 차감한다. */
class PointLot(
    val id: Long?,
    val memberId: Long,
    val amount: Long,
    remainingAmount: Long,
    val sourceType: PointSourceType,
    val sourceId: Long,
    val expiresAt: LocalDateTime,
) {
    var remainingAmount: Long = remainingAmount
        private set

    /** 남은 양 안에서 [amount] 만큼 차감하고 실제로 차감한 양을 돌려준다. */
    fun consume(amount: Long): Long {
        require(amount >= 0) { "차감량은 0 이상이어야 한다." }
        val taken = minOf(amount, remainingAmount)
        remainingAmount -= taken
        return taken
    }

    fun restore(amount: Long) {
        require(amount >= 0) { "복구량은 0 이상이어야 한다." }
        check(remainingAmount + amount <= this.amount) { "로트 원금을 넘겨 복구할 수 없다: lotId=$id" }
        remainingAmount += amount
    }

    fun expire() {
        remainingAmount = 0
    }

    fun isExpired(now: LocalDateTime): Boolean = !expiresAt.isAfter(now)
}
