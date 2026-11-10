package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import com.dogyun.kairos.order.domain.OrderStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

@Entity
@Table(name = "orders")
@SQLRestriction("status = 'ACTIVE'")
class OrderEntity(
    @Column(name = "order_no", nullable = false, updatable = false, length = 32)
    val orderNo: String,
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Column(name = "receiver_name", nullable = false, updatable = false, length = 50)
    val receiverName: String,
    @Column(name = "receiver_phone", nullable = false, updatable = false, length = 20)
    val receiverPhone: String,
    @Column(name = "zip_code", nullable = false, updatable = false, length = 10)
    val zipCode: String,
    @Column(name = "address1", nullable = false, updatable = false, length = 200)
    val address1: String,
    @Column(name = "address2", nullable = false, updatable = false, length = 200)
    val address2: String,
    @Column(name = "delivery_memo", updatable = false, length = 200)
    val deliveryMemo: String?,
    @Column(name = "items_amount", nullable = false, updatable = false)
    val itemsAmount: Long,
    @Column(name = "buyer_shipping_fee", nullable = false, updatable = false)
    val buyerShippingFee: Long,
    @Column(name = "total_amount", nullable = false, updatable = false)
    val totalAmount: Long,
    @Column(name = "free_shipping_applied", nullable = false, updatable = false)
    val freeShippingApplied: Boolean,
    @Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: LocalDateTime,
    orderStatus: OrderStatus,
    shippingFeeRecovered: Long,
    refundedAmount: Long,
    paidAt: LocalDateTime?,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 20)
    var orderStatus: OrderStatus = orderStatus
        protected set

    @Column(name = "shipping_fee_recovered", nullable = false)
    var shippingFeeRecovered: Long = shippingFeeRecovered
        protected set

    @Column(name = "refunded_amount", nullable = false)
    var refundedAmount: Long = refundedAmount
        protected set

    @Column(name = "paid_at")
    var paidAt: LocalDateTime? = paidAt
        protected set

    fun change(orderStatus: OrderStatus, shippingFeeRecovered: Long, refundedAmount: Long, paidAt: LocalDateTime?) {
        this.orderStatus = orderStatus
        this.shippingFeeRecovered = shippingFeeRecovered
        this.refundedAmount = refundedAmount
        this.paidAt = paidAt
    }
}
