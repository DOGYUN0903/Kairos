package com.dogyun.kairos.benefit.application

import com.dogyun.kairos.benefit.domain.CouponPolicy
import com.dogyun.kairos.benefit.domain.CouponPolicyRepository
import com.dogyun.kairos.benefit.domain.CouponPolicyStatus
import com.dogyun.kairos.benefit.domain.CouponType
import com.dogyun.kairos.benefit.domain.IssuerType
import com.dogyun.kairos.benefit.domain.TargetType
import com.dogyun.kairos.common.domain.PageResult
import java.time.LocalDateTime

class FakeCouponPolicyRepository : CouponPolicyRepository {
    val policies = linkedMapOf<Long, CouponPolicy>()
    private var nextId = 1L

    override fun save(policy: CouponPolicy): CouponPolicy {
        val id = policy.id ?: nextId++
        val saved = copy(policy, id, policy.issuedCount, policy.status)
        policies[id] = saved
        return saved
    }

    override fun findById(id: Long): CouponPolicy? = policies[id]?.let { copy(it, it.id, it.issuedCount, it.status) }

    override fun findAllByIds(ids: Collection<Long>): List<CouponPolicy> = ids.mapNotNull { findById(it) }

    override fun findClaimable(productId: Long?, sellerId: Long?, now: LocalDateTime, page: Int, size: Int): PageResult<CouponPolicy> {
        val all = policies.values.filter {
            it.status == CouponPolicyStatus.ACTIVE && !now.isBefore(it.validFrom) && now.isBefore(it.validUntil) &&
                (it.totalQuantity == null || it.issuedCount < it.totalQuantity) &&
                (
                    it.couponType == CouponType.CART || it.targetType == TargetType.ALL_PRODUCTS ||
                        (it.targetType == TargetType.PRODUCT && productId != null && it.targetProductId == productId) ||
                        (it.targetType == TargetType.SELLER && sellerId != null && it.sellerId == sellerId)
                    )
        }
        return paged(all.sortedByDescending { it.id }, page, size)
    }

    override fun findPageByIssuer(issuerType: IssuerType, sellerId: Long?, page: Int, size: Int): PageResult<CouponPolicy> =
        paged(policies.values.filter { it.issuerType == issuerType && (sellerId == null || it.sellerId == sellerId) }.sortedByDescending { it.id }, page, size)

    override fun tryIncreaseIssued(policyId: Long): Boolean {
        val p = policies[policyId] ?: return false
        if (p.totalQuantity != null && p.issuedCount >= p.totalQuantity) return false
        policies[policyId] = copy(p, p.id, p.issuedCount + 1, p.status)
        return true
    }

    private fun paged(list: List<CouponPolicy>, page: Int, size: Int) =
        PageResult.of(list.drop(page * size).take(size), page, size, list.size.toLong())

    private fun copy(p: CouponPolicy, id: Long?, issued: Int, status: CouponPolicyStatus) = CouponPolicy(
        id, p.name, p.issuerType, p.sellerId, p.couponType, p.targetType, p.targetProductId, p.discountType, p.discountValue,
        p.maxDiscount, p.minOrderAmount, p.totalQuantity, issued, p.validityDays, p.validFrom, p.validUntil, status,
    )
}
