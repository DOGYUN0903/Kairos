package com.dogyun.kairos.benefit.infrastructure

import com.dogyun.kairos.benefit.domain.PointSourceType
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
import java.time.LocalDateTime

@Entity
@Table(name = "point_lot")
@SQLRestriction("status = 'ACTIVE'")
class PointLotEntity(
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Column(name = "amount", nullable = false, updatable = false)
    val amount: Long,
    remainingAmount: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, updatable = false, length = 20)
    val sourceType: PointSourceType,
    @Column(name = "source_id", nullable = false, updatable = false)
    val sourceId: Long,
    @Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: LocalDateTime,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Column(name = "remaining_amount", nullable = false)
    var remainingAmount: Long = remainingAmount
        protected set

    fun changeRemaining(value: Long) {
        remainingAmount = value
    }
}
