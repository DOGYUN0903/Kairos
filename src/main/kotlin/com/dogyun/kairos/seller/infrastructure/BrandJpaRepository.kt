package com.dogyun.kairos.seller.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface BrandJpaRepository : JpaRepository<BrandEntity, Long> {
    fun existsByName(name: String): Boolean
    fun findAllBySellerIdOrderByIdDesc(sellerId: Long): List<BrandEntity>
}
