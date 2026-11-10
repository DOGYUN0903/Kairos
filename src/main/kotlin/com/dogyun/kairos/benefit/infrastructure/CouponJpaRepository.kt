package com.dogyun.kairos.benefit.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

interface CouponJpaRepository : JpaRepository<CouponEntity, Long> {
    fun findByPolicyIdAndMemberId(policyId: Long, memberId: Long): CouponEntity?

    @Query("select c.policyId from CouponEntity c where c.memberId = :memberId and c.policyId in :policyIds")
    fun findPolicyIdsByMember(@Param("memberId") memberId: Long, @Param("policyIds") policyIds: Collection<Long>): List<Long>

    @Query("select c from CouponEntity c where c.couponStatus = com.dogyun.kairos.benefit.domain.CouponStatus.USED and c.usedOrderId = :orderId order by c.id")
    fun findUsedByOrderId(@Param("orderId") orderId: Long): List<CouponEntity>

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update coupon set coupon_status = 'USED', used_at = :now where id = :id and member_id = :memberId " +
            "and coupon_status = 'AVAILABLE' and expires_at > :now and status = 'ACTIVE'",
        nativeQuery = true,
    )
    fun markUsed(@Param("id") id: Long, @Param("memberId") memberId: Long, @Param("now") now: LocalDateTime): Int

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update coupon set used_order_id = :orderId where id in :ids and coupon_status = 'USED'", nativeQuery = true)
    fun attachOrder(@Param("ids") ids: Collection<Long>, @Param("orderId") orderId: Long): Int

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update coupon set coupon_status = 'AVAILABLE', used_order_id = null, used_at = null " +
            "where id = :id and coupon_status = 'USED' and expires_at > :now and status = 'ACTIVE'",
        nativeQuery = true,
    )
    fun restoreIfNotExpired(@Param("id") id: Long, @Param("now") now: LocalDateTime): Int
}
