package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * 좋아요 행. 조회(내 좋아요 목록·`liked` 채우기)에만 쓰고, 쓰기는 [ProductLikeJpaRepository] 의 조건부 native 쿼리다.
 * `liked` 는 도메인 플래그이고 `status` 는 소프트 딜리트용으로 항상 ACTIVE 다.
 */
@Entity
@Table(name = "product_like")
@SQLRestriction("status = 'ACTIVE'")
class ProductLikeEntity(
    @Column(name = "product_id", nullable = false, updatable = false)
    val productId: Long,

    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,

    @Column(name = "liked", nullable = false, updatable = false)
    val liked: Boolean,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L
}
