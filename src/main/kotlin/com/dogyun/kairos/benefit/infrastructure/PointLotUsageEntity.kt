package com.dogyun.kairos.benefit.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "point_lot_usage")
@SQLRestriction("status = 'ACTIVE'")
class PointLotUsageEntity(
    @Column(name = "transaction_id", nullable = false, updatable = false)
    val transactionId: Long,
    @Column(name = "lot_id", nullable = false, updatable = false)
    val lotId: Long,
    @Column(name = "amount", nullable = false, updatable = false)
    val amount: Long,
    revertedAmount: Long,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Column(name = "reverted_amount", nullable = false)
    val revertedAmount: Long = revertedAmount
}
