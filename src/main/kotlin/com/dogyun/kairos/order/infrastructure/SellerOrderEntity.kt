package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import com.dogyun.kairos.order.domain.SellerOrderStatus
import com.dogyun.kairos.order.domain.ShippingFeePayer
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/** 주문 애그리거트 안의 판매자별 주문(같은 애그리거트). */
@Entity
@Table(name = "seller_order")
@SQLRestriction("status = 'ACTIVE'")
class SellerOrderEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    val order: OrderEntity,
    @Column(name = "seller_id", nullable = false, updatable = false)
    val sellerId: Long,
    @Column(name = "seller_name", nullable = false, updatable = false, length = 100)
    val sellerName: String,
    @Column(name = "commission_rate_bp", nullable = false, updatable = false)
    val commissionRateBp: Int,
    @Column(name = "shipping_fee", nullable = false, updatable = false)
    val shippingFee: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_fee_payer", nullable = false, updatable = false, length = 20)
    val shippingFeePayer: ShippingFeePayer,
    sellerOrderStatus: SellerOrderStatus,
    carrierCode: String?,
    trackingNumber: String?,
    shippedAt: LocalDateTime?,
    deliveredAt: LocalDateTime?,
    confirmedAt: LocalDateTime?,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(name = "seller_order_status", nullable = false, length = 20)
    var sellerOrderStatus: SellerOrderStatus = sellerOrderStatus
        protected set

    @Column(name = "carrier_code", length = 20)
    var carrierCode: String? = carrierCode
        protected set

    @Column(name = "tracking_number", length = 50)
    var trackingNumber: String? = trackingNumber
        protected set

    @Column(name = "shipped_at")
    var shippedAt: LocalDateTime? = shippedAt
        protected set

    @Column(name = "delivered_at")
    var deliveredAt: LocalDateTime? = deliveredAt
        protected set

    @Column(name = "confirmed_at")
    var confirmedAt: LocalDateTime? = confirmedAt
        protected set

    fun change(
        sellerOrderStatus: SellerOrderStatus,
        carrierCode: String?,
        trackingNumber: String?,
        shippedAt: LocalDateTime?,
        deliveredAt: LocalDateTime?,
        confirmedAt: LocalDateTime?,
    ) {
        this.sellerOrderStatus = sellerOrderStatus
        this.carrierCode = carrierCode
        this.trackingNumber = trackingNumber
        this.shippedAt = shippedAt
        this.deliveredAt = deliveredAt
        this.confirmedAt = confirmedAt
    }
}
