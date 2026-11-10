package com.dogyun.kairos.settlement.application

import com.dogyun.kairos.settlement.domain.AdjustmentType
import com.dogyun.kairos.settlement.domain.SettlementStatus
import java.time.LocalDate
import java.time.LocalDateTime

data class GenerateSettlementResult(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val sellerCount: Int,
    val createdCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val alreadyGenerated: Boolean,
)

data class SettlementSummary(
    val settlementId: Long,
    val sellerId: Long,
    val sellerName: String,
    val sellerStatus: String,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val payoutDate: LocalDate,
    val status: SettlementStatus,
    val totalAmount: Long,
    val payableAmount: Long,
    val lineCount: Int,
)

data class SettlementLineResult(
    val lineId: Long,
    val sellerOrderId: Long,
    val orderId: Long,
    val orderNo: String,
    val confirmedAt: LocalDateTime,
    val salesAmount: Long,
    val sellerCouponDiscount: Long,
    val commissionBase: Long,
    val commissionRateBp: Int,
    val commissionAmount: Long,
    val shippingFeeOwed: Long,
    val returnFeeBorne: Long,
    val lineAmount: Long,
)

data class SettlementAdjustmentResult(
    val adjustmentId: Long,
    val sellerId: Long,
    val settlementId: Long?,
    val type: AdjustmentType,
    val amount: Long,
    val reason: String,
    val refSellerOrderId: Long?,
    val sourceSettlementId: Long?,
    val createdAt: LocalDateTime?,
)

data class SettlementDetail(
    val settlementId: Long,
    val sellerId: Long,
    val sellerName: String,
    val sellerStatus: String,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val payoutDate: LocalDate,
    val status: SettlementStatus,
    val totalAmount: Long,
    val payableAmount: Long,
    val lineCount: Int,
    val salesAmount: Long,
    val sellerCouponDiscount: Long,
    val commissionAmount: Long,
    val shippingFeeOwed: Long,
    val returnFeeBorne: Long,
    val adjustmentAmount: Long,
    val confirmedAt: LocalDateTime?,
    val paidAt: LocalDateTime?,
    val paidMemo: String?,
    val lines: List<SettlementLineResult>,
    val adjustments: List<SettlementAdjustmentResult>,
)
