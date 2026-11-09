package com.dogyun.kairos.seller.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface BrandImageJpaRepository : JpaRepository<BrandImageEntity, Long> {
    fun findAllByBrandIdInAndPurpose(brandIds: Collection<Long>, purpose: String): List<BrandImageEntity>
}
