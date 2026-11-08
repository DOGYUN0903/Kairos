package com.dogyun.kairos.deal.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/** deal 과는 ID(deal_id)로만 참조한다. */
@Entity
@Table(name = "stock")
@SQLRestriction("status = 'ACTIVE'")
class StockEntity(
    @Column(name = "deal_id", nullable = false)
    val dealId: Long,
    totalQuantity: Int,
    remainingQuantity: Int,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Column(name = "total_quantity", nullable = false)
    var totalQuantity: Int = totalQuantity
        protected set

    @Column(name = "remaining_quantity", nullable = false)
    var remainingQuantity: Int = remainingQuantity
        protected set

    fun change(totalQuantity: Int, remainingQuantity: Int) {
        this.totalQuantity = totalQuantity
        this.remainingQuantity = remainingQuantity
    }
}
