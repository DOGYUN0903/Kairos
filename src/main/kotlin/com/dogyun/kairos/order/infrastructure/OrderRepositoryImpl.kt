package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.common.domain.Money
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.OrderStatus
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class OrderRepositoryImpl(
    private val jpaRepository: OrderJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : OrderRepository {

    override fun save(order: Order): Order {
        val id = order.id
        val entity = if (id == null) {
            OrderEntity(order.memberId, order.dealId, order.quantity, order.amount.amount, order.status, order.cancelReason)
        } else {
            requireNotNull(jpaRepository.findByIdOrNull(id)) { "존재하지 않는 주문입니다: $id" }
                .also { it.change(order.status, order.cancelReason) }
        }
        return toDomain(jpaRepository.saveAndFlush(entity))
    }

    override fun findById(id: Long): Order? = jpaRepository.findByIdOrNull(id)?.let(::toDomain)

    override fun sumActiveQuantity(memberId: Long, dealId: Long): Int {
        val order = QOrderEntity.orderEntity
        return queryFactory.select(order.quantity.sumAggregate())
            .from(order)
            .where(
                order.memberId.eq(memberId),
                order.dealId.eq(dealId),
                order.orderStatus.ne(OrderStatus.CANCELLED),
            )
            .fetchOne() ?: 0
    }

    override fun findPageByMemberId(memberId: Long, page: Int, size: Int): PageResult<Order> {
        val order = QOrderEntity.orderEntity
        val content = queryFactory.selectFrom(order)
            .where(order.memberId.eq(memberId))
            .orderBy(order.id.desc())
            .offset(page.toLong() * size)
            .limit(size.toLong())
            .fetch()
        val total = queryFactory.select(order.count()).from(order).where(order.memberId.eq(memberId)).fetchOne() ?: 0L
        return PageResult.of(content.map(::toDomain), page, size, total)
    }

    private fun toDomain(e: OrderEntity) =
        Order.restore(e.id, e.memberId, e.dealId, e.quantity, Money(e.amount), e.orderStatus, e.cancelReason, e.createdAt)
}
