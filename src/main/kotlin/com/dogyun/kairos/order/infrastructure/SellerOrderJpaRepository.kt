package com.dogyun.kairos.order.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

interface SellerOrderJpaRepository : JpaRepository<SellerOrderEntity, Long> {
    @Query("select s from SellerOrderEntity s where s.order.id in :orderIds order by s.id asc")
    fun findAllByOrderIdInOrderByIdAsc(@Param("orderIds") orderIds: Collection<Long>): List<SellerOrderEntity>

    @Query("select s.order.id from SellerOrderEntity s where s.id = :sellerOrderId")
    fun findOrderIdById(@Param("sellerOrderId") sellerOrderId: Long): Long?

    /**
     * 주문 행 락을 잡기 전에 주문 id 를 찾는 조회. 바깥 트랜잭션에서 읽으면 REPEATABLE READ 스냅샷이 락보다 먼저 만들어져,
     * 락을 잡은 뒤에도 그 전 상태(예: 방금 커밋된 항목 취소 요청)를 읽게 된다. 그래서 별도 트랜잭션에서 읽는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @Query("select s.order.id from SellerOrderEntity s where s.id = :sellerOrderId")
    fun findOrderIdOutsideTransaction(@Param("sellerOrderId") sellerOrderId: Long): Long?
}
