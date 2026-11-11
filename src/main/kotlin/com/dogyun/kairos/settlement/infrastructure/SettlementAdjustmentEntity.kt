package com.dogyun.kairos.settlement.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import com.dogyun.kairos.settlement.domain.AdjustmentType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * 정산 조정. `settlement_id` 는 정산에 붙을 때 조건부 UPDATE 로만 채워진다(엔티티로는 바꾸지 않는다).
 * 삭제는 소프트(`status = DELETED`).
 */
@Entity
@Table(name = "settlement_adjustment")
@SQLRestriction("status = 'ACTIVE'")
class SettlementAdjustmentEntity(
    @Column(name = "seller_id", nullable = false, updatable = false)
    val sellerId: Long,
    @Column(name = "settlement_id", insertable = false, updatable = false)
    val settlementId: Long?,
    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, updatable = false, length = 20)
    val adjustmentType: AdjustmentType,
    @Column(name = "amount", nullable = false, updatable = false)
    val amount: Long,
    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    val reason: String,
    @Column(name = "ref_seller_order_id", updatable = false)
    val refSellerOrderId: Long?,
    @Column(name = "source_settlement_id", updatable = false)
    val sourceSettlementId: Long?,
    @Column(name = "created_by_admin_id", updatable = false)
    val createdByAdminId: Long?,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L
}
