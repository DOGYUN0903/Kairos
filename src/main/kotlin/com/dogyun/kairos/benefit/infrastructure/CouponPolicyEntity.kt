package com.dogyun.kairos.benefit.infrastructure

import com.dogyun.kairos.benefit.domain.CouponPolicyStatus
import com.dogyun.kairos.benefit.domain.CouponType
import com.dogyun.kairos.benefit.domain.DiscountType
import com.dogyun.kairos.benefit.domain.IssuerType
import com.dogyun.kairos.benefit.domain.TargetType
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
@Table(name = "coupon_policy")
@SQLRestriction("status = 'ACTIVE'")
class CouponPolicyEntity(
    @Column(name = "name", nullable = false, updatable = false, length = 50)
    val name: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "issuer_type", nullable = false, updatable = false, length = 20)
    val issuerType: IssuerType,
    @Column(name = "seller_id", updatable = false)
    val sellerId: Long?,
    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_type", nullable = false, updatable = false, length = 20)
    val couponType: CouponType,
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 20)
    val targetType: TargetType,
    @Column(name = "target_product_id", updatable = false)
    val targetProductId: Long?,
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, updatable = false, length = 20)
    val discountType: DiscountType,
    @Column(name = "discount_value", nullable = false, updatable = false)
    val discountValue: Long,
    @Column(name = "max_discount", updatable = false)
    val maxDiscount: Long?,
    @Column(name = "min_order_amount", updatable = false)
    val minOrderAmount: Long?,
    @Column(name = "total_quantity", updatable = false)
    val totalQuantity: Int?,
    @Column(name = "validity_days", updatable = false)
    val validityDays: Int?,
    @Column(name = "valid_from", nullable = false, updatable = false)
    val validFrom: LocalDateTime,
    @Column(name = "valid_until", nullable = false, updatable = false)
    val validUntil: LocalDateTime,
    policyStatus: CouponPolicyStatus,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    /** 조건부 UPDATE(`tryIncreaseIssued`)로만 바뀐다. 동시 발급을 덮어쓰지 않도록 엔티티 UPDATE 에서 제외한다. */
    @Column(name = "issued_count", nullable = false, insertable = false, updatable = false)
    val issuedCount: Int = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_status", nullable = false, length = 20)
    var policyStatus: CouponPolicyStatus = policyStatus
        protected set

    fun changeStatus(status: CouponPolicyStatus) {
        this.policyStatus = status
    }
}
