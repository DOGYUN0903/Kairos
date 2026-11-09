package com.dogyun.kairos.cart.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface CartItemJpaRepository : JpaRepository<CartItemEntity, Long> {
    fun findAllByMemberIdOrderById(memberId: Long): List<CartItemEntity>
}
