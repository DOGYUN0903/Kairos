package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import com.dogyun.kairos.order.domain.CancelReason
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

/** member/deal 과는 ID 로만 참조한다. */
@Entity
@Table(name = "orders")
@SQLRestriction("status = 'ACTIVE'")
class OrderEntity(
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
    @Column(name = "deal_id", nullable = false)
    val dealId: Long,
    @Column(name = "quantity", nullable = false)
    val quantity: Int,
    @Column(name = "amount", nullable = false)
    val amount: Long,
    orderStatus: OrderStatus,
    cancelReason: CancelReason?,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 20)
    var orderStatus: OrderStatus = orderStatus
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", length = 30)
    var cancelReason: CancelReason? = cancelReason
        protected set

    fun change(orderStatus: OrderStatus, cancelReason: CancelReason?) {
        this.orderStatus = orderStatus
        this.cancelReason = cancelReason
    }
}
