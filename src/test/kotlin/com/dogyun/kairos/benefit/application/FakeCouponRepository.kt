package com.dogyun.kairos.benefit.application

import com.dogyun.kairos.benefit.domain.Coupon
import com.dogyun.kairos.benefit.domain.CouponRepository
import com.dogyun.kairos.benefit.domain.CouponStatus
import com.dogyun.kairos.common.domain.PageResult
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDateTime

class FakeCouponRepository : CouponRepository {
    val coupons = linkedMapOf<Long, Coupon>()
    private var nextId = 1L

    override fun save(coupon: Coupon): Coupon {
        if (coupon.id == null && coupons.values.any { it.policyId == coupon.policyId && it.memberId == coupon.memberId }) {
            throw DataIntegrityViolationException("uk_coupon_policy_member")
        }
        val id = coupon.id ?: nextId++
        val saved = copy(coupon, id, coupon.status, coupon.usedOrderId, coupon.usedAt)
        coupons[id] = saved
        return saved
    }

    override fun findById(id: Long): Coupon? = coupons[id]
    override fun findAllByIds(ids: Collection<Long>): List<Coupon> = ids.mapNotNull { coupons[it] }
    override fun findByPolicyAndMember(policyId: Long, memberId: Long): Coupon? =
        coupons.values.firstOrNull { it.policyId == policyId && it.memberId == memberId }

    override fun findClaimedPolicyIds(memberId: Long, policyIds: Collection<Long>): Set<Long> =
        coupons.values.filter { it.memberId == memberId && it.policyId in policyIds }.map { it.policyId }.toSet()

    override fun findPageByMember(memberId: Long, status: String?, now: LocalDateTime, page: Int, size: Int): PageResult<Coupon> {
        val all = coupons.values.filter { it.memberId == memberId && (status == null || it.displayStatus(now) == status) }
            .sortedByDescending { it.id }
        return PageResult.of(all.drop(page * size).take(size), page, size, all.size.toLong())
    }

    override fun markUsed(couponId: Long, memberId: Long, now: LocalDateTime): Boolean {
        val c = coupons[couponId] ?: return false
        if (c.memberId != memberId || c.status != CouponStatus.AVAILABLE || c.isExpired(now)) return false
        coupons[couponId] = copy(c, c.id, CouponStatus.USED, null, now)
        return true
    }

    override fun attachOrder(couponIds: Collection<Long>, orderId: Long) {
        couponIds.forEach { id -> coupons[id]?.let { coupons[id] = copy(it, it.id, it.status, orderId, it.usedAt) } }
    }

    override fun restoreIfNotExpired(couponId: Long, now: LocalDateTime): Boolean {
        val c = coupons[couponId] ?: return false
        if (c.status != CouponStatus.USED || c.isExpired(now)) return false
        coupons[couponId] = copy(c, c.id, CouponStatus.AVAILABLE, null, null)
        return true
    }

    override fun findAvailableByMember(memberId: Long, now: LocalDateTime): List<Coupon> =
        coupons.values.filter { it.memberId == memberId && it.status == CouponStatus.AVAILABLE && !it.isExpired(now) }.sortedBy { it.id }

    override fun findUsedByOrderId(orderId: Long): List<Coupon> =
        coupons.values.filter { it.status == CouponStatus.USED && it.usedOrderId == orderId }

    private fun copy(c: Coupon, id: Long?, status: CouponStatus, orderId: Long?, usedAt: LocalDateTime?) =
        Coupon(id, c.policyId, c.memberId, status, orderId, c.issuedAt, c.expiresAt, usedAt)
}
