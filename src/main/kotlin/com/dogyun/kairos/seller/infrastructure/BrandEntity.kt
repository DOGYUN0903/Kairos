package com.dogyun.kairos.seller.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import com.dogyun.kairos.seller.domain.BrandStatus
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
@Table(name = "brand")
@SQLRestriction("status = 'ACTIVE'")
class BrandEntity(
    @Column(name = "seller_id", nullable = false, updatable = false)
    val sellerId: Long,
    name: String,
    englishName: String?,
    description: String?,
    brandStatus: BrandStatus,
    rejectReason: String?,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Column(name = "name", nullable = false, length = 50)
    var name: String = name
        protected set

    @Column(name = "english_name", length = 100)
    var englishName: String? = englishName
        protected set

    @Column(name = "description", length = 1000)
    var description: String? = description
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "brand_status", nullable = false, length = 20)
    var brandStatus: BrandStatus = brandStatus
        protected set

    @Column(name = "reject_reason", length = 500)
    var rejectReason: String? = rejectReason
        protected set

    fun change(
        name: String,
        englishName: String?,
        description: String?,
        brandStatus: BrandStatus,
        rejectReason: String?,
    ) {
        this.name = name
        this.englishName = englishName
        this.description = description
        this.brandStatus = brandStatus
        this.rejectReason = rejectReason
    }
}
