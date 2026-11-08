package com.dogyun.kairos.deal.infrastructure

import com.dogyun.kairos.common.domain.Money
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.deal.domain.Deal
import com.dogyun.kairos.deal.domain.DealId
import com.dogyun.kairos.deal.domain.DealRepository
import com.dogyun.kairos.deal.domain.DealStatus
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class DealRepositoryImpl(private val jpaRepository: DealJpaRepository) : DealRepository {

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
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        val result = when (status) {
            null -> jpaRepository.findAll(pageable)
            DealStatus.SCHEDULED -> jpaRepository.findByOpenAtAfter(now, pageable)
            DealStatus.ONGOING -> jpaRepository.findByOpenAtLessThanEqualAndCloseAtAfter(now, now, pageable)
            DealStatus.CLOSED -> jpaRepository.findByCloseAtLessThanEqual(now, pageable)
        }
        return PageResult.of(result.content.map(::toDomain), page, size, result.totalElements)
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
