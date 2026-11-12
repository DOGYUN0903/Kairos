package com.dogyun.kairos.benefit.infrastructure

import com.dogyun.kairos.benefit.domain.Coupon
import com.dogyun.kairos.benefit.domain.CouponRepository
import com.dogyun.kairos.benefit.domain.CouponStatus
import com.dogyun.kairos.common.domain.PageResult
import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class CouponRepositoryImpl(
    private val jpaRepository: CouponJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : CouponRepository {

    override fun save(coupon: Coupon): Coupon {
        require(coupon.id == null) { "쿠폰 상태는 조건부 UPDATE 로만 바꾼다." }
        val saved = jpaRepository.saveAndFlush(CouponEntity(coupon.policyId, coupon.memberId, coupon.issuedAt, coupon.expiresAt))
        return requireNotNull(findFresh(saved.id))
    }

    override fun findById(id: Long): Coupon? = findFresh(id)

    override fun findAllByIds(ids: Collection<Long>): List<Coupon> {
        if (ids.isEmpty()) return emptyList()
        val c = QCouponEntity.couponEntity
        return queryFactory.selectFrom(c).where(c.id.`in`(ids)).orderBy(c.id.asc()).fetch().map(::toDomain)
    }

    override fun findByPolicyAndMember(policyId: Long, memberId: Long): Coupon? =
        jpaRepository.findByPolicyIdAndMemberId(policyId, memberId)?.let(::toDomain)

    override fun findClaimedPolicyIds(memberId: Long, policyIds: Collection<Long>): Set<Long> =
        if (policyIds.isEmpty()) emptySet() else jpaRepository.findPolicyIdsByMember(memberId, policyIds).toSet()

    override fun findPageByMember(memberId: Long, status: String?, now: LocalDateTime, page: Int, size: Int): PageResult<Coupon> {
        val c = QCouponEntity.couponEntity
        val where = BooleanBuilder().and(c.memberId.eq(memberId))
        when (status) {
            "USED" -> where.and(c.couponStatus.eq(CouponStatus.USED))
            "AVAILABLE" -> where.and(c.couponStatus.eq(CouponStatus.AVAILABLE)).and(c.expiresAt.gt(now))
            "EXPIRED" -> where.and(c.couponStatus.eq(CouponStatus.AVAILABLE)).and(c.expiresAt.loe(now))
        }
        val content = queryFactory.selectFrom(c).where(where).orderBy(c.id.desc())
            .offset(page.toLong() * size).limit(size.toLong()).fetch().map(::toDomain)
        val total = queryFactory.select(c.count()).from(c).where(where).fetchOne() ?: 0L
        return PageResult.of(content, page, size, total)
    }

    override fun markUsed(couponId: Long, memberId: Long, now: LocalDateTime): Boolean =
        jpaRepository.markUsed(couponId, memberId, now) == 1

    override fun attachOrder(couponIds: Collection<Long>, orderId: Long) {
        if (couponIds.isNotEmpty()) jpaRepository.attachOrder(couponIds, orderId)
    }

    override fun restoreIfNotExpired(couponId: Long, now: LocalDateTime): Boolean =
        jpaRepository.restoreIfNotExpired(couponId, now) == 1

    override fun findAvailableByMember(memberId: Long, now: LocalDateTime): List<Coupon> {
        val c = QCouponEntity.couponEntity
        return queryFactory.selectFrom(c)
            .where(c.memberId.eq(memberId), c.couponStatus.eq(CouponStatus.AVAILABLE), c.expiresAt.gt(now))
            .orderBy(c.id.asc()).fetch().map(::toDomain)
    }

    override fun findUsedByOrderId(orderId: Long): List<Coupon> = jpaRepository.findUsedByOrderId(orderId).map(::toDomain)

    /** 조건부 UPDATE 가 영속성 컨텍스트를 거치지 않으므로 항상 DB 에서 새로 읽는다. */
    private fun findFresh(id: Long): Coupon? {
        val c = QCouponEntity.couponEntity
        return queryFactory.selectFrom(c).where(c.id.eq(id)).fetchOne()?.let(::toDomain)
    }

    private fun toDomain(e: CouponEntity) = Coupon(e.id, e.policyId, e.memberId, e.couponStatus, e.usedOrderId, e.issuedAt, e.expiresAt, e.usedAt)
}
