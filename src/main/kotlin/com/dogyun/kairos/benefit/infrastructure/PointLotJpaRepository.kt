package com.dogyun.kairos.benefit.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface PointLotJpaRepository : JpaRepository<PointLotEntity, Long> {
    @Query(
        "select coalesce(sum(l.remainingAmount), 0) from PointLotEntity l " +
            "where l.memberId = :memberId and l.remainingAmount > 0 and l.expiresAt > :from and l.expiresAt <= :to",
    )
    fun sumExpiringBetween(@Param("memberId") memberId: Long, @Param("from") from: LocalDateTime, @Param("to") to: LocalDateTime): Long

    @Query("select l from PointLotEntity l where l.remainingAmount > 0 and l.expiresAt <= :now and l.id > :afterId order by l.id")
    fun findExpired(@Param("now") now: LocalDateTime, @Param("afterId") afterId: Long, pageable: org.springframework.data.domain.Pageable): List<PointLotEntity>
}
