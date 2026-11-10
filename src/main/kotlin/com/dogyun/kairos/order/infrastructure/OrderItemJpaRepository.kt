package com.dogyun.kairos.order.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OrderItemJpaRepository : JpaRepository<OrderItemEntity, Long> {
    @Query("select i.sellerOrder.order.id from OrderItemEntity i where i.id = :orderItemId")
    fun findOrderIdById(@Param("orderItemId") orderItemId: Long): Long?

    @Query("select i from OrderItemEntity i where i.sellerOrder.id in :sellerOrderIds order by i.id asc")
    fun findAllBySellerOrderIdInOrderByIdAsc(@Param("sellerOrderIds") sellerOrderIds: Collection<Long>): List<OrderItemEntity>
}
