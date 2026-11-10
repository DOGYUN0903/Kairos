package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import com.dogyun.kairos.order.domain.CancelActor
import com.dogyun.kairos.order.domain.CouponIssuer
import com.dogyun.kairos.order.domain.OrderItemStatus
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

/** 주문 시점 스냅샷을 가진 주문 항목(같은 애그리거트). */
@Entity
@Table(name = "order_item")
@SQLRestriction("status = 'ACTIVE'")
class OrderItemEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_order_id", nullable = false, updatable = false)
    val sellerOrder: SellerOrderEntity,
    @Column(name = "sku_id", nullable = false, updatable = false)
    val skuId: Long,
    @Column(name = "product_id", nullable = false, updatable = false)
    val productId: Long,
    @Column(name = "product_name", nullable = false, updatable = false, length = 100)
    val productName: String,
    @Column(name = "brand_name", nullable = false, updatable = false, length = 50)
    val brandName: String,
    @Column(name = "option_display_name", nullable = false, updatable = false, length = 120)
    val optionDisplayName: String,
    @Column(name = "sale_price", nullable = false, updatable = false)
    val salePrice: Long,
    @Column(name = "extra_price", nullable = false, updatable = false)
    val extraPrice: Long,
    @Column(name = "quantity", nullable = false, updatable = false)
    val quantity: Int,
    orderItemStatus: OrderItemStatus,
    cancelActor: CancelActor?,
    cancelReason: String?,
    refundAmount: Long,
    shippingFeeDeducted: Long,
    shippingFeeReturned: Long,
    canceledAt: LocalDateTime?,
    @Column(name = "product_coupon_id", updatable = false)
    val productCouponId: Long?,
    @Column(name = "product_coupon_discount", nullable = false, updatable = false)
    val productCouponDiscount: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "product_coupon_issuer", updatable = false, length = 20)
    val productCouponIssuer: CouponIssuer?,
    @Column(name = "cart_coupon_allocated", nullable = false, updatable = false)
    val cartCouponAllocated: Long,
    @Column(name = "points_allocated", nullable = false, updatable = false)
    val pointsAllocated: Long,
    @Column(name = "paid_amount", nullable = false, updatable = false)
    val paidAmount: Long,
    pointsRefunded: Long,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(name = "order_item_status", nullable = false, length = 20)
    var orderItemStatus: OrderItemStatus = orderItemStatus
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_actor", length = 20)
    var cancelActor: CancelActor? = cancelActor
        protected set

    @Column(name = "cancel_reason", length = 500)
    var cancelReason: String? = cancelReason
        protected set

    @Column(name = "refund_amount", nullable = false)
    var refundAmount: Long = refundAmount
        protected set

    @Column(name = "shipping_fee_deducted", nullable = false)
    var shippingFeeDeducted: Long = shippingFeeDeducted
        protected set

    @Column(name = "shipping_fee_returned", nullable = false)
    var shippingFeeReturned: Long = shippingFeeReturned
        protected set

    @Column(name = "canceled_at")
    var canceledAt: LocalDateTime? = canceledAt
        protected set

    @Column(name = "points_refunded", nullable = false)
    var pointsRefunded: Long = pointsRefunded
        protected set

    fun change(
        orderItemStatus: OrderItemStatus,
        cancelActor: CancelActor?,
        cancelReason: String?,
        refundAmount: Long,
        shippingFeeDeducted: Long,
        shippingFeeReturned: Long,
        canceledAt: LocalDateTime?,
        pointsRefunded: Long,
    ) {
        this.pointsRefunded = pointsRefunded
        this.orderItemStatus = orderItemStatus
        this.cancelActor = cancelActor
        this.cancelReason = cancelReason
        this.refundAmount = refundAmount
        this.shippingFeeDeducted = shippingFeeDeducted
        this.shippingFeeReturned = shippingFeeReturned
        this.canceledAt = canceledAt
    }
}
