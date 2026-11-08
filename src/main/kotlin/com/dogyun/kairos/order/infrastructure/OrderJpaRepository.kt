package com.dogyun.kairos.order.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface OrderJpaRepository : JpaRepository<OrderEntity, Long> {
    fun findAllByMemberIdOrderByIdDesc(memberId: Long): List<OrderEntity>
}
