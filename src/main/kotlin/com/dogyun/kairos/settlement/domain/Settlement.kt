package com.dogyun.kairos.settlement.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.LocalDateTime

class Settlement(
    val id: Long?,
    val sellerId: Long,
    val period: SettlementPeriod,
    status: SettlementStatus,
    val lines: List<SettlementLine>,
    val salesAmount: Long,
    val sellerCouponDiscount: Long,
    val commissionAmount: Long,
    val shippingFeeOwed: Long,
    val returnFeeBorne: Long,
    adjustmentAmount: Long,
    totalAmount: Long,
    payableAmount: Long,
    val lineCount: Int,
    confirmedAt: LocalDateTime? = null,
    paidAt: LocalDateTime? = null,
    paidMemo: String? = null,
) {
    var status: SettlementStatus = status
        private set
    var adjustmentAmount: Long = adjustmentAmount
        private set
    var totalAmount: Long = totalAmount
        private set
    var payableAmount: Long = payableAmount
        private set
    var confirmedAt: LocalDateTime? = confirmedAt
        private set
    var paidAt: LocalDateTime? = paidAt
        private set
    var paidMemo: String? = paidMemo
        private set

    /** 지급하지 못해 다음 정산으로 넘길 금액(0 또는 음수). */
    val carryOverAmount: Long get() = totalAmount - payableAmount

    /** 생성 트랜잭션 안에서 붙은 조정 합계를 반영한다. GENERATED에서만. */
    fun applyAdjustments(amount: Long) {
        check(status == SettlementStatus.GENERATED) { "GENERATED 정산만 조정을 반영할 수 있다: $status" }
        adjustmentAmount = amount
        totalAmount = lines.sumOf { it.calc.lineAmount } + amount
        payableAmount = maxOf(totalAmount, 0L)
    }

    fun confirm(now: LocalDateTime) {
        if (status != SettlementStatus.GENERATED) return
        status = SettlementStatus.CONFIRMED
        confirmedAt = now
    }

    fun markPaid(now: LocalDateTime, memo: String?) {
        if (memo != null && memo.length > MEMO_MAX) throw DomainException(ErrorCode.INVALID_REQUEST)
        when (status) {
            SettlementStatus.PAID -> return
            SettlementStatus.GENERATED -> throw DomainException(ErrorCode.SETTLEMENT_INVALID_STATE_TRANSITION)
            SettlementStatus.CONFIRMED -> {
                status = SettlementStatus.PAID
                paidAt = now
                paidMemo = memo
            }
        }
    }

    companion object {
        const val MEMO_MAX = 200

        fun generate(
            sellerId: Long,
            period: SettlementPeriod,
            sources: List<SettlementSource>,
            adjustmentAmount: Long,
        ): Settlement {
            val lines = sources.sortedBy { it.sellerOrderId }.map {
                check(it.sellerId == sellerId) { "다른 판매자의 주문이 섞였다: ${it.sellerOrderId}" }
                SettlementLine(null, it.sellerOrderId, it.orderId, it.orderNo, it.confirmedAt, SettlementCalculator.line(it))
            }
            val total = lines.sumOf { it.calc.lineAmount } + adjustmentAmount
            return Settlement(
                id = null,
                sellerId = sellerId,
                period = period,
                status = SettlementStatus.GENERATED,
                lines = lines,
                salesAmount = lines.sumOf { it.calc.salesAmount },
                sellerCouponDiscount = lines.sumOf { it.calc.sellerCouponDiscount },
                commissionAmount = lines.sumOf { it.calc.commissionAmount },
                shippingFeeOwed = lines.sumOf { it.calc.shippingFeeOwed },
                returnFeeBorne = lines.sumOf { it.calc.returnFeeBorne },
                adjustmentAmount = adjustmentAmount,
                totalAmount = total,
                payableAmount = maxOf(total, 0L),
                lineCount = lines.size,
            )
        }
    }
}
