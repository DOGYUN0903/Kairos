package com.dogyun.kairos.settlement.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 정산 라인(판매자 주문 1개의 스냅샷). 모든 열이 불변이다. `seller_order_id` UNIQUE 가 "한 번만 정산"을 지킨다. */
@Entity
@Table(name = "settlement_line")
class SettlementLineEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id", nullable = false, updatable = false)
    val settlement: SettlementEntity,
    @Column(name = "seller_order_id", nullable = false, updatable = false)
    val sellerOrderId: Long,
    @Column(name = "order_id", nullable = false, updatable = false)
    val orderId: Long,
    @Column(name = "order_no", nullable = false, updatable = false, length = 32)
    val orderNo: String,
    @Column(name = "confirmed_at", nullable = false, updatable = false)
    val confirmedAt: LocalDateTime,
    @Column(name = "sales_amount", nullable = false, updatable = false)
    val salesAmount: Long,
    @Column(name = "seller_coupon_discount", nullable = false, updatable = false)
    val sellerCouponDiscount: Long,
    @Column(name = "commission_base", nullable = false, updatable = false)
    val commissionBase: Long,
    @Column(name = "commission_rate_bp", nullable = false, updatable = false)
    val commissionRateBp: Int,
    @Column(name = "commission_amount", nullable = false, updatable = false)
    val commissionAmount: Long,
    @Column(name = "shipping_fee_owed", nullable = false, updatable = false)
    val shippingFeeOwed: Long,
    @Column(name = "return_fee_borne", nullable = false, updatable = false)
    val returnFeeBorne: Long,
    @Column(name = "line_amount", nullable = false, updatable = false)
    val lineAmount: Long,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L
}
