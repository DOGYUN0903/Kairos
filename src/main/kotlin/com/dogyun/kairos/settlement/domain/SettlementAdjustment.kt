package com.dogyun.kairos.settlement.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.LocalDateTime

class SettlementAdjustment(
    val id: Long?,
    val sellerId: Long,
    val settlementId: Long?,
    val type: AdjustmentType,
    val amount: Long,
    val reason: String,
    val refSellerOrderId: Long?,
    val sourceSettlementId: Long?,
    val createdByAdminId: Long?,
    val createdAt: LocalDateTime?,
) {
    val applied: Boolean get() = settlementId != null

    companion object {
        const val REASON_MAX = 500

        fun manual(sellerId: Long, amount: Long, reason: String, refSellerOrderId: Long?, adminId: Long): SettlementAdjustment {
            if (amount == 0L || reason.isBlank() || reason.length > REASON_MAX) {
                throw DomainException(ErrorCode.SETTLEMENT_ADJUSTMENT_INVALID)
            }
            return SettlementAdjustment(null, sellerId, null, AdjustmentType.MANUAL, amount, reason, refSellerOrderId, null, adminId, null)
        }

        fun carryOver(sellerId: Long, amount: Long, sourceSettlementId: Long): SettlementAdjustment {
            require(amount < 0) { "이월 금액은 음수여야 한다: $amount" }
            return SettlementAdjustment(
                null, sellerId, null, AdjustmentType.CARRY_OVER, amount,
                "정산 #$sourceSettlementId 음수 이월", null, sourceSettlementId, null, null,
            )
        }
    }
}
