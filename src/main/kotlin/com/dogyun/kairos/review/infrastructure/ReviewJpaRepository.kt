package com.dogyun.kairos.review.infrastructure

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ReviewJpaRepository : JpaRepository<ReviewEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReviewEntity r where r.id = :id")
    fun findLockedById(@Param("id") id: Long): ReviewEntity?

    /** `@SQLRestriction` 을 피해 삭제된 행까지 본다. */
    @Query("select count(*) from review where order_item_id = :orderItemId", nativeQuery = true)
    fun countAnyByOrderItemId(@Param("orderItemId") orderItemId: Long): Long

    fun findAllByProductId(productId: Long, pageable: Pageable): Page<ReviewEntity>
    fun findAllByMemberId(memberId: Long, pageable: Pageable): Page<ReviewEntity>
    fun findAllBySellerId(sellerId: Long, pageable: Pageable): Page<ReviewEntity>
}
