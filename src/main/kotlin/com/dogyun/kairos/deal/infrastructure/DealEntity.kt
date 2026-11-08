package com.dogyun.kairos.deal.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

@Entity
@Table(name = "deal")
@SQLRestriction("status = 'ACTIVE'")
class DealEntity(
    productName: String,
    originalPrice: Long,
    dealPrice: Long,
    totalQuantity: Int,
    perMemberLimit: Int,
    openAt: LocalDateTime,
    closeAt: LocalDateTime,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Column(name = "product_name", nullable = false, length = 255)
    var productName: String = productName
        protected set

    @Column(name = "original_price", nullable = false)
    var originalPrice: Long = originalPrice
        protected set

    @Column(name = "deal_price", nullable = false)
    var dealPrice: Long = dealPrice
        protected set

    @Column(name = "total_quantity", nullable = false)
    var totalQuantity: Int = totalQuantity
        protected set

    @Column(name = "per_member_limit", nullable = false)
    var perMemberLimit: Int = perMemberLimit
        protected set

    @Column(name = "open_at", nullable = false)
    var openAt: LocalDateTime = openAt
        protected set

    @Column(name = "close_at", nullable = false)
    var closeAt: LocalDateTime = closeAt
        protected set

    fun change(
        productName: String,
        originalPrice: Long,
        dealPrice: Long,
        totalQuantity: Int,
        perMemberLimit: Int,
        openAt: LocalDateTime,
        closeAt: LocalDateTime,
    ) {
        this.productName = productName
        this.originalPrice = originalPrice
        this.dealPrice = dealPrice
        this.totalQuantity = totalQuantity
        this.perMemberLimit = perMemberLimit
        this.openAt = openAt
        this.closeAt = closeAt
    }
}
