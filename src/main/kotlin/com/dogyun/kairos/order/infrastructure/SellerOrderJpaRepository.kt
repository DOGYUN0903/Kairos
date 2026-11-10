package com.dogyun.kairos.order.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SellerOrderJpaRepository : JpaRepository<SellerOrderEntity, Long> {
    @Query("select s from SellerOrderEntity s where s.order.id in :orderIds order by s.id asc")
    fun findAllByOrderIdInOrderByIdAsc(@Param("orderIds") orderIds: Collection<Long>): List<SellerOrderEntity>

    @Query("select s.order.id from SellerOrderEntity s where s.id = :sellerOrderId")
    fun findOrderIdById(@Param("sellerOrderId") sellerOrderId: Long): Long?
}
