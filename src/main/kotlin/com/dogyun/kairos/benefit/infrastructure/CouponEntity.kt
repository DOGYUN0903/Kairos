package com.dogyun.kairos.benefit.infrastructure

import com.dogyun.kairos.benefit.domain.CouponStatus
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

/** 사용·복구는 [CouponJpaRepository] 의 조건부 UPDATE 로만 바뀌므로 상태 컬럼은 엔티티 UPDATE 에서 제외한다. */
@Entity
@Table(name = "coupon")
@SQLRestriction("status = 'ACTIVE'")
class CouponEntity(
    @Column(name = "policy_id", nullable = false, updatable = false)
    val policyId: Long,
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Column(name = "issued_at", nullable = false, updatable = false)
    val issuedAt: LocalDateTime,
    @Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: LocalDateTime,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_status", nullable = false, length = 20, updatable = false)
    val couponStatus: CouponStatus = CouponStatus.AVAILABLE

    @Column(name = "used_order_id", insertable = false, updatable = false)
    val usedOrderId: Long? = null

    @Column(name = "used_at", insertable = false, updatable = false)
    val usedAt: LocalDateTime? = null
}
