package com.dogyun.kairos.deal.infrastructure

import com.dogyun.kairos.common.domain.Money
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.deal.domain.Deal
import com.dogyun.kairos.deal.domain.DealId
import com.dogyun.kairos.deal.domain.DealRepository
import com.dogyun.kairos.deal.domain.DealStatus
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class DealRepositoryImpl(private val jpaRepository: DealJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : DealRepository {

    override fun save(deal: Deal): Deal {
        val id = deal.id
        val entity = if (id == null) {
            DealEntity(
                deal.productName, deal.originalPrice.amount, deal.dealPrice.amount,
                deal.totalQuantity, deal.perMemberLimit, deal.openAt, deal.closeAt,
            )
        } else {
            requireNotNull(jpaRepository.findByIdOrNull(id.value)) { "존재하지 않는 딜입니다: ${id.value}" }.also {
                it.change(
                    deal.productName, deal.originalPrice.amount, deal.dealPrice.amount,
                    deal.totalQuantity, deal.perMemberLimit, deal.openAt, deal.closeAt,
                )
            }
        }
        return toDomain(jpaRepository.saveAndFlush(entity))
    }

    override fun findById(id: DealId): Deal? = jpaRepository.findByIdOrNull(id.value)?.let(::toDomain)

    override fun findPage(status: DealStatus?, now: LocalDateTime, page: Int, size: Int): PageResult<Deal> {
        val deal = QDealEntity.dealEntity
        val where = statusCondition(deal, status, now)
        val content = queryFactory.selectFrom(deal)
            .where(where)
            .orderBy(deal.id.desc())
            .offset(page.toLong() * size)
            .limit(size.toLong())
            .fetch()
        val total = queryFactory.select(deal.count()).from(deal).where(where).fetchOne() ?: 0L
        return PageResult.of(content.map(::toDomain), page, size, total)
    }

    // status가 null이면 null을 반환해 조건이 붙지 않는다(QueryDSL where는 null을 무시한다).
    private fun statusCondition(deal: QDealEntity, status: DealStatus?, now: LocalDateTime): BooleanExpression? =
        when (status) {
            null -> null
            DealStatus.SCHEDULED -> deal.openAt.gt(now)
            DealStatus.ONGOING -> deal.openAt.loe(now).and(deal.closeAt.gt(now))
            DealStatus.CLOSED -> deal.closeAt.loe(now)
        }

    override fun delete(id: DealId) {
        jpaRepository.findByIdOrNull(id.value)?.let {
            it.softDelete()
            jpaRepository.saveAndFlush(it)
        }
    }

    private fun toDomain(entity: DealEntity) = Deal(
        id = DealId(entity.id),
        productName = entity.productName,
        originalPrice = Money(entity.originalPrice),
        dealPrice = Money(entity.dealPrice),
        totalQuantity = entity.totalQuantity,
        perMemberLimit = entity.perMemberLimit,
        openAt = entity.openAt,
        closeAt = entity.closeAt,
    )
}
