package com.dogyun.kairos.benefit.infrastructure

import com.dogyun.kairos.benefit.domain.CouponPolicy
import com.dogyun.kairos.benefit.domain.CouponPolicyRepository
import com.dogyun.kairos.benefit.domain.CouponPolicyStatus
import com.dogyun.kairos.benefit.domain.CouponType
import com.dogyun.kairos.benefit.domain.IssuerType
import com.dogyun.kairos.benefit.domain.TargetType
import com.dogyun.kairos.common.domain.PageResult
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.Predicate
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class CouponPolicyRepositoryImpl(
    private val jpaRepository: CouponPolicyJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : CouponPolicyRepository {

    override fun save(policy: CouponPolicy): CouponPolicy {
        val entity = policy.id?.let { jpaRepository.findByIdOrNull(it)?.apply { changeStatus(policy.status) } }
            ?: CouponPolicyEntity(
                policy.name, policy.issuerType, policy.sellerId, policy.couponType, policy.targetType, policy.targetProductId,
                policy.discountType, policy.discountValue, policy.maxDiscount, policy.minOrderAmount, policy.totalQuantity,
                policy.validityDays, policy.validFrom, policy.validUntil, policy.status,
            )
        jpaRepository.saveAndFlush(entity)
        // issued_count 는 DB 기본값/조건부 UPDATE 가 채우므로 다시 읽어 최신 값을 돌려준다.
        jpaRepository.flush()
        return requireNotNull(findFresh(entity.id))
    }

    override fun findById(id: Long): CouponPolicy? = findFresh(id)

    override fun findAllByIds(ids: Collection<Long>): List<CouponPolicy> =
        if (ids.isEmpty()) emptyList() else jpaRepository.findAllById(ids).map(::toDomain)

    override fun findClaimable(productId: Long?, sellerId: Long?, now: LocalDateTime, page: Int, size: Int): PageResult<CouponPolicy> {
        val p = QCouponPolicyEntity.couponPolicyEntity
        val target = BooleanBuilder()
            .or(p.couponType.eq(CouponType.CART))
            .or(p.targetType.eq(TargetType.ALL_PRODUCTS))
        productId?.let { target.or(p.targetType.eq(TargetType.PRODUCT).and(p.targetProductId.eq(it))) }
        sellerId?.let { target.or(p.targetType.eq(TargetType.SELLER).and(p.sellerId.eq(it))) }
        val where = BooleanBuilder()
            .and(p.policyStatus.eq(CouponPolicyStatus.ACTIVE))
            .and(p.validFrom.loe(now))
            .and(p.validUntil.gt(now))
            .and(p.totalQuantity.isNull.or(p.issuedCount.lt(p.totalQuantity)))
            .and(target)
        return paged(where, page, size)
    }

    override fun findPageByIssuer(issuerType: IssuerType, sellerId: Long?, page: Int, size: Int): PageResult<CouponPolicy> {
        val p = QCouponPolicyEntity.couponPolicyEntity
        val where = BooleanBuilder().and(p.issuerType.eq(issuerType))
        sellerId?.let { where.and(p.sellerId.eq(it)) }
        return paged(where, page, size)
    }

    override fun tryIncreaseIssued(policyId: Long): Boolean = jpaRepository.increaseIssued(policyId) == 1

    private fun paged(where: Predicate, page: Int, size: Int): PageResult<CouponPolicy> {
        val p = QCouponPolicyEntity.couponPolicyEntity
        val content = queryFactory.selectFrom(p).where(where).orderBy(p.id.desc())
            .offset(page.toLong() * size).limit(size.toLong()).fetch().map(::toDomain)
        val total = queryFactory.select(p.count()).from(p).where(where).fetchOne() ?: 0L
        return PageResult.of(content, page, size, total)
    }

    /** 1차 캐시에 낡은 issued_count 가 남지 않도록 영속성 컨텍스트에서 떼고 읽는다. */
    private fun findFresh(id: Long): CouponPolicy? {
        val p = QCouponPolicyEntity.couponPolicyEntity
        return queryFactory.selectFrom(p).where(p.id.eq(id)).fetchOne()
            ?.let(::toDomain)
    }

    private fun toDomain(e: CouponPolicyEntity) = CouponPolicy(
        e.id, e.name, e.issuerType, e.sellerId, e.couponType, e.targetType, e.targetProductId, e.discountType, e.discountValue,
        e.maxDiscount, e.minOrderAmount, e.totalQuantity, e.issuedCount, e.validityDays, e.validFrom, e.validUntil, e.policyStatus,
    )
}
