package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/** 상품과 파일의 연결(같은 애그리거트). 파일은 ID 로만 참조한다. 이미지 순서가 바뀌면 소프트 딜리트 후 다시 만든다. */
@Entity
@Table(name = "product_image")
@SQLRestriction("status = 'ACTIVE'")
class ProductImageEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    val product: ProductEntity,

    @Column(name = "file_id", nullable = false, updatable = false)
    val fileId: Long,

    @Column(name = "display_order", nullable = false, updatable = false)
    val displayOrder: Int,

    @Column(name = "is_main", nullable = false, updatable = false)
    val isMain: Boolean,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L
}
