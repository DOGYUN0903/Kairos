package com.dogyun.kairos.catalog.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import org.hibernate.annotations.SQLRestriction

/**
 * seller 모듈의 `seller` 테이블을 상품 검색 조인용으로만 읽는 뷰 엔티티(ADR 0002).
 * "모든 엔티티는 BaseEntity 상속" 규칙의 명시적 예외(읽기 전용, 감사 컬럼 미매핑). 소프트 딜리트는 `@SQLRestriction` 으로 거른다.
 */
@Entity
@Immutable
@Table(name = "seller")
@SQLRestriction("status = 'ACTIVE'")
class SellerView(
    @Id
    @Column(name = "id")
    val id: Long,

    /** seller 모듈 SellerStatus 의 이름. 타입 의존을 피하려고 문자열로 둔다. */
    @Column(name = "seller_status", nullable = false, length = 20)
    val sellerStatus: String,
)
