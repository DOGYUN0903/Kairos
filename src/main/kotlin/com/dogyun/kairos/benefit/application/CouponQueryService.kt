package com.dogyun.kairos.benefit.application

import com.dogyun.kairos.benefit.domain.CouponPolicyRepository
import com.dogyun.kairos.benefit.domain.CouponRepository
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class CouponQueryService(
    private val policyRepository: CouponPolicyRepository,
    private val couponRepository: CouponRepository,
    private val productReader: BenefitProductReader,
    private val clock: Clock,
) {

    /** 발급 가능한 정책. [productId] 가 있으면 그 상품·판매자 대상 정책도 포함한다. */
    fun listClaimable(memberId: Long, productId: Long?, page: Int, size: Int): PageResult<CouponPolicyResult> {
        val ref = productId?.let { productReader.findProduct(it) ?: throw DomainException(ErrorCode.PRODUCT_NOT_FOUND) }
        val result = policyRepository.findClaimable(ref?.productId, ref?.sellerId, LocalDateTime.now(clock), checkedPage(page), checkedSize(size))
        val claimed = couponRepository.findClaimedPolicyIds(memberId, result.content.mapNotNull { it.id })
        return PageResult(
            result.content.map { it.toResult(it.id in claimed) },
            result.page, result.size, result.totalElements, result.totalPages, result.hasNext,
        )
    }

    /** [status] 는 `AVAILABLE`/`USED`/`EXPIRED` 계산값 필터. */
    fun listMine(memberId: Long, status: String?, page: Int, size: Int): PageResult<CouponResult> {
        if (status != null && status !in STATUSES) throw DomainException(ErrorCode.INVALID_REQUEST)
        val now = LocalDateTime.now(clock)
        val result = couponRepository.findPageByMember(memberId, status, now, checkedPage(page), checkedSize(size))
        val policies = policyRepository.findAllByIds(result.content.map { it.policyId }.toSet()).associateBy { it.id }
        val content = result.content.mapNotNull { coupon ->
            val policy = policies[coupon.policyId] ?: return@mapNotNull null
            CouponResult(
                couponId = requireNotNull(coupon.id),
                policyId = coupon.policyId,
                name = policy.name,
                couponType = policy.couponType.name,
                discountType = policy.discountType.name,
                discountValue = policy.discountValue,
                maxDiscount = policy.maxDiscount,
                minOrderAmount = policy.minOrderAmount,
                status = coupon.displayStatus(now),
                issuedAt = coupon.issuedAt,
                expiresAt = coupon.expiresAt,
                usedOrderId = coupon.usedOrderId,
                usedAt = coupon.usedAt,
            )
        }
        return PageResult(content, result.page, result.size, result.totalElements, result.totalPages, result.hasNext)
    }

    private companion object {
        val STATUSES = setOf("AVAILABLE", "USED", "EXPIRED")
    }
}
