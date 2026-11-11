package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.catalog.domain.ChangeRequestStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ProductChangeRequestJpaRepository : JpaRepository<ProductChangeRequestEntity, Long> {
    fun findFirstByProductIdAndChangeStatus(productId: Long, changeStatus: ChangeRequestStatus): ProductChangeRequestEntity?
    fun findFirstByProductIdOrderByIdDesc(productId: Long): ProductChangeRequestEntity?
    fun findAllByProductIdInAndChangeStatus(productIds: Collection<Long>, changeStatus: ChangeRequestStatus): List<ProductChangeRequestEntity>
    fun findAllByOrderByIdAsc(pageable: Pageable): Page<ProductChangeRequestEntity>
    fun findAllByChangeStatusOrderByIdAsc(changeStatus: ChangeRequestStatus, pageable: Pageable): Page<ProductChangeRequestEntity>
    fun findAllByOrderByIdDesc(pageable: Pageable): Page<ProductChangeRequestEntity>
    fun findAllByChangeStatusOrderByIdDesc(changeStatus: ChangeRequestStatus, pageable: Pageable): Page<ProductChangeRequestEntity>
}
