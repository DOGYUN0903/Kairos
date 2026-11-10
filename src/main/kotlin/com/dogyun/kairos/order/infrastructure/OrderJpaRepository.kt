package com.dogyun.kairos.order.infrastructure

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OrderJpaRepository : JpaRepository<OrderEntity, Long> {
    fun findByOrderNo(orderNo: String): OrderEntity?

    /** `SELECT … FOR UPDATE`. 주문 상태를 바꾸는 트랜잭션이 가장 먼저 부른다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    fun findLockedById(@Param("id") id: Long): OrderEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.orderNo = :orderNo")
    fun findLockedByOrderNo(@Param("orderNo") orderNo: String): OrderEntity?
}
