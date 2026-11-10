package com.dogyun.kairos.payment.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import com.dogyun.kairos.payment.domain.PaymentStatus
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
@Table(name = "payment")
@SQLRestriction("status = 'ACTIVE'")
class PaymentEntity(
    @Column(name = "order_id", nullable = false, updatable = false)
    val orderId: Long,
    @Column(name = "order_no", nullable = false, updatable = false, length = 32)
    val orderNo: String,
    @Column(name = "amount", nullable = false, updatable = false)
    val amount: Long,
    paymentKey: String?,
    refundedAmount: Long,
    paymentStatus: PaymentStatus,
    failReason: String?,
    inProgressAt: LocalDateTime?,
    approvedAt: LocalDateTime?,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Column(name = "payment_key", length = 200)
    var paymentKey: String? = paymentKey
        protected set

    @Column(name = "refunded_amount", nullable = false)
    var refundedAmount: Long = refundedAmount
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    var paymentStatus: PaymentStatus = paymentStatus
        protected set

    @Column(name = "fail_reason", length = 100)
    var failReason: String? = failReason
        protected set

    @Column(name = "in_progress_at")
    var inProgressAt: LocalDateTime? = inProgressAt
        protected set

    @Column(name = "approved_at")
    var approvedAt: LocalDateTime? = approvedAt
        protected set

    fun change(
        paymentKey: String?,
        refundedAmount: Long,
        paymentStatus: PaymentStatus,
        failReason: String?,
        inProgressAt: LocalDateTime?,
        approvedAt: LocalDateTime?,
    ) {
        this.paymentKey = paymentKey
        this.refundedAmount = refundedAmount
        this.paymentStatus = paymentStatus
        this.failReason = failReason
        this.inProgressAt = inProgressAt
        this.approvedAt = approvedAt
    }
}
