package com.dogyun.kairos.seller.infrastructure

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.ListOrder
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.seller.domain.BusinessNumber
import com.dogyun.kairos.seller.domain.Seller
import com.dogyun.kairos.seller.domain.SellerId
import com.dogyun.kairos.seller.domain.SellerRepository
import com.dogyun.kairos.seller.domain.SellerStatus
import com.dogyun.kairos.seller.domain.SettlementAccount
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class SellerRepositoryImpl(
    private val jpaRepository: SellerJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : SellerRepository {

    override fun save(seller: Seller): Seller {
        val id = seller.id
        val entity = if (id == null) {
            SellerEntity(
                seller.memberId, seller.companyName, seller.businessNumber.value, seller.representativeName,
                seller.phone, seller.settlementAccount.bank, seller.settlementAccount.accountNumber,
                seller.settlementAccount.holder, seller.commissionRateBp, seller.status,
                seller.rejectReason, seller.suspendReason,
            )
        } else {
            requireNotNull(jpaRepository.findByIdOrNull(id.value)) { "존재하지 않는 판매자입니다: ${id.value}" }.also {
                it.change(
                    seller.companyName, seller.businessNumber.value, seller.representativeName, seller.phone,
                    seller.settlementAccount.bank, seller.settlementAccount.accountNumber,
                    seller.settlementAccount.holder, seller.commissionRateBp, seller.status,
                    seller.rejectReason, seller.suspendReason,
                )
            }
        }
        return try {
            toDomain(jpaRepository.saveAndFlush(entity))
        } catch (e: DataIntegrityViolationException) {
            // 사전 검사를 통과한 동시 요청은 UNIQUE 제약이 막는다.
            val duplicatedMember = e.message?.contains("uk_seller_member_id") == true
            throw DomainException(
                if (duplicatedMember) ErrorCode.SELLER_ALREADY_EXISTS else ErrorCode.SELLER_BUSINESS_NUMBER_DUPLICATED,
            )
        }
    }

    override fun findById(id: SellerId): Seller? = jpaRepository.findByIdOrNull(id.value)?.let(::toDomain)

    override fun findAllByIds(ids: Collection<SellerId>): List<Seller> =
        if (ids.isEmpty()) emptyList() else jpaRepository.findAllById(ids.map { it.value }).map(::toDomain)

    override fun findByMemberId(memberId: Long): Seller? = jpaRepository.findByMemberId(memberId)?.let(::toDomain)

    override fun existsByBusinessNumber(businessNumber: BusinessNumber): Boolean =
        jpaRepository.existsByBusinessNumber(businessNumber.value)

    override fun findPage(status: SellerStatus?, order: ListOrder, page: Int, size: Int): PageResult<Seller> {
        val seller = QSellerEntity.sellerEntity
        // status가 null이면 where 조건이 붙지 않는다(QueryDSL where는 null을 무시한다).
        val where = status?.let { seller.sellerStatus.eq(it) }
        val content = queryFactory.selectFrom(seller)
            .where(where)
            .orderBy(if (order == ListOrder.OLDEST) seller.id.asc() else seller.id.desc())
            .offset(page.toLong() * size)
            .limit(size.toLong())
            .fetch()
        val total = queryFactory.select(seller.count()).from(seller).where(where).fetchOne() ?: 0L
        return PageResult.of(content.map(::toDomain), page, size, total)
    }

    private fun toDomain(entity: SellerEntity) = Seller.restore(
        id = SellerId(entity.id),
        memberId = entity.memberId,
        companyName = entity.companyName,
        businessNumber = BusinessNumber(entity.businessNumber),
        representativeName = entity.representativeName,
        phone = entity.phone,
        settlementAccount = SettlementAccount(entity.bank, entity.accountNumber, entity.accountHolder),
        commissionRateBp = entity.commissionRateBp,
        status = entity.sellerStatus,
        rejectReason = entity.rejectReason,
        suspendReason = entity.suspendReason,
        createdAt = entity.createdAt,
    )
}
