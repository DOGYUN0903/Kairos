package com.dogyun.kairos.seller.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/** 브랜드와 파일의 연결. 파일은 ID 로만 참조한다. */
@Entity
@Table(name = "brand_image")
@SQLRestriction("status = 'ACTIVE'")
class BrandImageEntity(
    @Column(name = "brand_id", nullable = false, updatable = false)
    val brandId: Long,

    @Column(name = "file_id", nullable = false, updatable = false)
    val fileId: Long,

    @Column(name = "purpose", nullable = false, length = 20, updatable = false)
    val purpose: String,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    companion object {
        const val PURPOSE_LOGO = "LOGO"
    }
}
