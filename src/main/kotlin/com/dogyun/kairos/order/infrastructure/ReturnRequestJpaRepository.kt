package com.dogyun.kairos.order.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ReturnRequestJpaRepository : JpaRepository<ReturnRequestEntity, Long> {
    @Query("select r from ReturnRequestEntity r where r.orderItem.id in :orderItemIds")
    fun findAllByOrderItemIdIn(@Param("orderItemIds") orderItemIds: Collection<Long>): List<ReturnRequestEntity>

    @Query("select r.orderId from ReturnRequestEntity r where r.id = :returnId")
    fun findOrderIdById(@Param("returnId") returnId: Long): Long?
}
