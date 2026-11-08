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

@Entity
@Table(name = "payment")
@SQLRestriction("status = 'ACTIVE'")
class PaymentEntity(
    orderId: Long,
    amount: Long,
    paymentStatus: PaymentStatus,
    failReason: String?,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Column(name = "order_id", nullable = false)
    var orderId: Long = orderId
        protected set

    @Column(name = "amount", nullable = false)
    var amount: Long = amount
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    var paymentStatus: PaymentStatus = paymentStatus
        protected set

    @Column(name = "fail_reason", length = 30)
    var failReason: String? = failReason
        protected set

    fun change(paymentStatus: PaymentStatus) {
        this.paymentStatus = paymentStatus
    }
}
