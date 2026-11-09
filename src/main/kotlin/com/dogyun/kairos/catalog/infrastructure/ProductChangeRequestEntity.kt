package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.catalog.domain.ChangeRequestStatus
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

@Entity
@Table(name = "product_change_request")
@SQLRestriction("status = 'ACTIVE'")
class ProductChangeRequestEntity(
    @Column(name = "product_id", nullable = false, updatable = false)
    val productId: Long,

    @Column(name = "seller_id", nullable = false, updatable = false)
    val sellerId: Long,

    @Column(name = "original_price", nullable = false, updatable = false)
    val originalPrice: Long,

    @Column(name = "content_json", nullable = false, columnDefinition = "TEXT", updatable = false)
    val contentJson: String,
    changeStatus: ChangeRequestStatus,
    rejectReason: String?,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(name = "change_status", nullable = false, length = 20)
    var changeStatus: ChangeRequestStatus = changeStatus
        protected set

    @Column(name = "reject_reason", length = 500)
    var rejectReason: String? = rejectReason
        protected set

    /** 요청 내용은 바뀌지 않고 상태·반려 사유만 바뀐다. */
    fun change(changeStatus: ChangeRequestStatus, rejectReason: String?) {
        this.changeStatus = changeStatus
        this.rejectReason = rejectReason
    }
}
