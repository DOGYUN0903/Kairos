package com.dogyun.kairos.benefit.infrastructure

import com.dogyun.kairos.benefit.domain.PointRefType
import com.dogyun.kairos.benefit.domain.PointTransactionType
import com.dogyun.kairos.common.infrastructure.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/** 원장은 쌓기만 한다. UNIQUE(transaction_type, ref_type, ref_id)가 멱등 키다. */
@Entity
@Table(name = "point_transaction")
@SQLRestriction("status = 'ACTIVE'")
class PointTransactionEntity(
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, updatable = false, length = 20)
    val transactionType: PointTransactionType,
    @Column(name = "amount", nullable = false, updatable = false)
    val amount: Long,
    @Column(name = "balance_after", nullable = false, updatable = false)
    val balanceAfter: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", nullable = false, updatable = false, length = 20)
    val refType: PointRefType,
    @Column(name = "ref_id", nullable = false, updatable = false)
    val refId: Long,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L
}
