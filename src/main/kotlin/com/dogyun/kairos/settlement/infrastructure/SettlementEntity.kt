package com.dogyun.kairos.settlement.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import com.dogyun.kairos.settlement.domain.SettlementStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 정산 헤더. 라인과 같은 애그리거트라 `@OneToMany(cascade = ALL)` 로 함께 저장한다(ADR 0005 예외: 모듈 안 같은 애그리거트).
 * 정산·라인은 삭제하지 않으므로 `@SQLRestriction` 이 없다. 생성 뒤에는 상태·합계 열만 바뀐다.
 */
@Entity
@Table(name = "settlement")
class SettlementEntity(
    @Column(name = "seller_id", nullable = false, updatable = false)
    val sellerId: Long,
    @Column(name = "period_start", nullable = false, updatable = false)
    val periodStart: LocalDate,
    @Column(name = "period_end", nullable = false, updatable = false)
    val periodEnd: LocalDate,
    @Column(name = "payout_date", nullable = false, updatable = false)
    val payoutDate: LocalDate,
    settlementStatus: SettlementStatus,
    @Column(name = "sales_amount", nullable = false, updatable = false)
    val salesAmount: Long,
    @Column(name = "seller_coupon_discount", nullable = false, updatable = false)
    val sellerCouponDiscount: Long,
    @Column(name = "commission_amount", nullable = false, updatable = false)
    val commissionAmount: Long,
    @Column(name = "shipping_fee_owed", nullable = false, updatable = false)
    val shippingFeeOwed: Long,
    @Column(name = "return_fee_borne", nullable = false, updatable = false)
    val returnFeeBorne: Long,
    adjustmentAmount: Long,
    totalAmount: Long,
    payableAmount: Long,
    @Column(name = "line_count", nullable = false, updatable = false)
    val lineCount: Int,
    confirmedAt: LocalDateTime?,
    paidAt: LocalDateTime?,
    paidMemo: String?,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false, length = 20)
    var settlementStatus: SettlementStatus = settlementStatus
        protected set

    @Column(name = "adjustment_amount", nullable = false)
    var adjustmentAmount: Long = adjustmentAmount
        protected set

    @Column(name = "total_amount", nullable = false)
    var totalAmount: Long = totalAmount
        protected set

    @Column(name = "payable_amount", nullable = false)
    var payableAmount: Long = payableAmount
        protected set

    @Column(name = "confirmed_at")
    var confirmedAt: LocalDateTime? = confirmedAt
        protected set

    @Column(name = "paid_at")
    var paidAt: LocalDateTime? = paidAt
        protected set

    @Column(name = "paid_memo", length = 200)
    var paidMemo: String? = paidMemo
        protected set

    @OneToMany(mappedBy = "settlement", cascade = [CascadeType.ALL])
    val lines: MutableList<SettlementLineEntity> = mutableListOf()

    fun change(
        settlementStatus: SettlementStatus,
        adjustmentAmount: Long,
        totalAmount: Long,
        payableAmount: Long,
        confirmedAt: LocalDateTime?,
        paidAt: LocalDateTime?,
        paidMemo: String?,
    ) {
        this.settlementStatus = settlementStatus
        this.adjustmentAmount = adjustmentAmount
        this.totalAmount = totalAmount
        this.payableAmount = payableAmount
        this.confirmedAt = confirmedAt
        this.paidAt = paidAt
        this.paidMemo = paidMemo
    }
}
