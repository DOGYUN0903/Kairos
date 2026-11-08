package com.dogyun.kairos.deal.infrastructure

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface DealJpaRepository : JpaRepository<DealEntity, Long> {
    fun findByOpenAtAfter(now: LocalDateTime, pageable: Pageable): Page<DealEntity>

    fun findByOpenAtLessThanEqualAndCloseAtAfter(
        openAt: LocalDateTime,
        closeAt: LocalDateTime,
        pageable: Pageable,
    ): Page<DealEntity>

    fun findByCloseAtLessThanEqual(now: LocalDateTime, pageable: Pageable): Page<DealEntity>
}
