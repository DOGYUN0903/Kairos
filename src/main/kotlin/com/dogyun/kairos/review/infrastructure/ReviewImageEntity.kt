package com.dogyun.kairos.review.infrastructure

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

@Entity
@Table(name = "review_image")
@SQLRestriction("status = 'ACTIVE'")
class ReviewImageEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false, updatable = false)
    val review: ReviewEntity,

    @Column(name = "file_id", nullable = false, updatable = false)
    val fileId: Long,

    @Column(name = "display_order", nullable = false, updatable = false)
    val displayOrder: Int,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L
}
