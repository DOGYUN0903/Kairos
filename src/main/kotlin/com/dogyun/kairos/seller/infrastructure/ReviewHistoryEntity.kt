package com.dogyun.kairos.seller.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import com.dogyun.kairos.seller.domain.ReviewResult
import com.dogyun.kairos.seller.domain.ReviewTarget
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
@Table(name = "seller_review_history")
@SQLRestriction("status = 'ACTIVE'")
class ReviewHistoryEntity(
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20, updatable = false)
    val targetType: ReviewTarget,

    @Column(name = "target_id", nullable = false, updatable = false)
    val targetId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20, updatable = false)
    val result: ReviewResult,

    @Column(name = "reason", length = 500, updatable = false)
    val reason: String?,

    @Column(name = "reviewer_id", nullable = false, updatable = false)
    val reviewerId: Long,

    @Column(name = "reviewed_at", nullable = false, updatable = false)
    val reviewedAt: LocalDateTime,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L
}
