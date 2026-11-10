package com.dogyun.kairos.benefit.domain

import java.time.LocalDateTime

enum class PointTransactionType { EARN_PURCHASE, EARN_REVIEW, USE, REFUND, EXPIRE }

enum class PointRefType { PURCHASE_CONFIRM, REVIEW, ORDER, ORDER_ITEM, POINT_LOT }

enum class PointSourceType { PURCHASE, REVIEW, REFUND }

/** 사용 거래가 로트에서 가져간 양. 항목 단위 되돌림은 [revertedAmount] 에 나눠 쌓인다. */
data class PointLotUsage(
    val id: Long?,
    val lotId: Long,
    val amount: Long,
    val revertedAmount: Long,
) {
    val revertible: Long get() = amount - revertedAmount

    fun reverted(by: Long): PointLotUsage {
        require(by in 0..revertible) { "되돌릴 수 있는 양을 넘었다." }
        return copy(revertedAmount = revertedAmount + by)
    }
}

/** 포인트 원장 한 건. [amount] 는 부호가 있다(적립·환급 양수, 사용·만료 음수). */
class PointTransaction(
    val id: Long?,
    val memberId: Long,
    val type: PointTransactionType,
    val amount: Long,
    val balanceAfter: Long,
    val refType: PointRefType,
    val refId: Long,
    val usages: List<PointLotUsage> = emptyList(),
    val createdAt: LocalDateTime? = null,
)
