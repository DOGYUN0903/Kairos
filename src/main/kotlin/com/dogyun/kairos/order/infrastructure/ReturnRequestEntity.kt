package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import com.dogyun.kairos.order.domain.DecidedBy
import com.dogyun.kairos.order.domain.FeeBearer
import com.dogyun.kairos.order.domain.ReturnReason
import com.dogyun.kairos.order.domain.ReturnRequest
import com.dogyun.kairos.order.domain.ReturnStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/** 주문 항목의 반품(같은 애그리거트). 항목 → 반품 방향의 연관은 두지 않고 반품 → 항목만 단방향으로 가진다. */
@Entity
@Table(name = "return_request")
@SQLRestriction("status = 'ACTIVE'")
class ReturnRequestEntity(
    @Column(name = "order_id", nullable = false, updatable = false)
    val orderId: Long,
    @Column(name = "seller_order_id", nullable = false, updatable = false)
    val sellerOrderId: Long,
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false, updatable = false)
    val orderItem: OrderItemEntity,
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Column(name = "seller_id", nullable = false, updatable = false)
    val sellerId: Long,
    returnReason: ReturnReason,
    detail: String?,
    returnStatus: ReturnStatus,
    returnFee: Long,
    outboundFeeRecovered: Long,
    shippingFeeRefund: Long,
    sellerBorneFee: Long,
    refundAmount: Long,
    pointsRefund: Long,
    rejectReason: String?,
    requestedAt: LocalDateTime,
    decidedAt: LocalDateTime?,
    decidedBy: DecidedBy?,
    receivedAt: LocalDateTime?,
    completedAt: LocalDateTime?,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(name = "return_reason", nullable = false, length = 20)
    var returnReason: ReturnReason = returnReason
        protected set

    @Column(name = "detail", length = 500)
    var detail: String? = detail
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "return_status", nullable = false, length = 20)
    var returnStatus: ReturnStatus = returnStatus
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_bearer", nullable = false, length = 10)
    var feeBearer: FeeBearer = returnReason.feeBearer
        protected set

    @Column(name = "return_fee", nullable = false)
    var returnFee: Long = returnFee
        protected set

    @Column(name = "outbound_fee_recovered", nullable = false)
    var outboundFeeRecovered: Long = outboundFeeRecovered
        protected set

    @Column(name = "shipping_fee_refund", nullable = false)
    var shippingFeeRefund: Long = shippingFeeRefund
        protected set

    @Column(name = "seller_borne_fee", nullable = false)
    var sellerBorneFee: Long = sellerBorneFee
        protected set

    @Column(name = "refund_amount", nullable = false)
    var refundAmount: Long = refundAmount
        protected set

    @Column(name = "points_refund", nullable = false)
    var pointsRefund: Long = pointsRefund
        protected set

    @Column(name = "reject_reason", length = 500)
    var rejectReason: String? = rejectReason
        protected set

    @Column(name = "requested_at", nullable = false)
    var requestedAt: LocalDateTime = requestedAt
        protected set

    @Column(name = "decided_at")
    var decidedAt: LocalDateTime? = decidedAt
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "decided_by", length = 10)
    var decidedBy: DecidedBy? = decidedBy
        protected set

    @Column(name = "received_at")
    var receivedAt: LocalDateTime? = receivedAt
        protected set

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = completedAt
        protected set

    fun change(request: ReturnRequest) {
        returnReason = request.reason
        detail = request.detail
        returnStatus = request.status
        feeBearer = request.feeBearer
        returnFee = request.returnFee
        outboundFeeRecovered = request.outboundFeeRecovered
        shippingFeeRefund = request.shippingFeeRefund
        sellerBorneFee = request.sellerBorneFee
        refundAmount = request.refundAmount
        pointsRefund = request.pointsRefund
        rejectReason = request.rejectReason
        requestedAt = request.requestedAt
        decidedAt = request.decidedAt
        decidedBy = request.decidedBy
        receivedAt = request.receivedAt
        completedAt = request.completedAt
    }

    fun toDomain() = ReturnRequest(
        id, orderId, sellerOrderId, orderItem.id, memberId, sellerId, returnReason, detail, returnStatus, returnFee,
        outboundFeeRecovered, shippingFeeRefund, sellerBorneFee, refundAmount, pointsRefund, rejectReason, requestedAt,
        decidedAt, decidedBy, receivedAt, completedAt,
    )
}
