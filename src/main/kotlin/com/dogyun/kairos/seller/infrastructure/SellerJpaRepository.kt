package com.dogyun.kairos.seller.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface SellerJpaRepository : JpaRepository<SellerEntity, Long> {
    fun findByMemberId(memberId: Long): SellerEntity?
    fun existsByBusinessNumber(businessNumber: String): Boolean
}
