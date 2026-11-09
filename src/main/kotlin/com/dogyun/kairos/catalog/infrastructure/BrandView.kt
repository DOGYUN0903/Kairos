package com.dogyun.kairos.catalog.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import org.hibernate.annotations.SQLRestriction

/**
 * seller 모듈의 `brand` 테이블을 상품 검색 조인용으로만 읽는 뷰 엔티티(ADR 0002).
 * "모든 엔티티는 BaseEntity 상속" 규칙의 명시적 예외: 읽기 전용이라 감사 컬럼을 매핑하지 않고,
 * 소프트 딜리트는 `@SQLRestriction` 으로 동일하게 거른다. 쓰기 경로는 없다(브랜드 변경은 seller 모듈만 한다).
 */
@Entity
@Immutable
@Table(name = "brand")
@SQLRestriction("status = 'ACTIVE'")
class BrandView(
    @Id
    @Column(name = "id")
    val id: Long,

    @Column(name = "seller_id", nullable = false)
    val sellerId: Long,

    @Column(name = "name", nullable = false, length = 50)
    val name: String,

    /** seller 모듈 BrandStatus 의 이름. 타입 의존을 피하려고 문자열로 둔다. */
    @Column(name = "brand_status", nullable = false, length = 20)
    val brandStatus: String,
)
