package com.dogyun.kairos.benefit.application

import com.dogyun.kairos.benefit.domain.Coupon
import com.dogyun.kairos.benefit.domain.CouponPolicyRepository
import com.dogyun.kairos.benefit.domain.CouponRepository
import com.dogyun.kairos.benefit.domain.CouponStatus
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class CouponService(
    private val policyRepository: CouponPolicyRepository,
    private val couponRepository: CouponRepository,
    private val clock: Clock,
) {

    /**
     * 수량은 조건부 UPDATE 의 영향 행 수로 판정한다(락 없음). 이미 받은 회원은 수량을 올리기 전에 거른다.
     * 동시 요청이 UNIQUE(policy_id, member_id) 에 걸리면 트랜잭션이 롤백되어 올린 `issued_count` 도 함께 돌아간다.
     */
    @Transactional
    fun claim(memberId: Long, policyId: Long): ClaimedCoupon {
        val now = LocalDateTime.now(clock)
        val policy = policyRepository.findById(policyId) ?: throw DomainException(ErrorCode.COUPON_POLICY_NOT_FOUND)
        policy.requireClaimable(now)
        if (couponRepository.findByPolicyAndMember(policyId, memberId) != null) {
            throw DomainException(ErrorCode.COUPON_ALREADY_ISSUED)
        }
        if (!policyRepository.tryIncreaseIssued(policyId)) throw DomainException(ErrorCode.COUPON_SOLD_OUT)
        val coupon = try {
            couponRepository.save(Coupon(null, policyId, memberId, CouponStatus.AVAILABLE, null, now, policy.expiresAtFor(now), null))
        } catch (e: DataIntegrityViolationException) {
            throw DomainException(ErrorCode.COUPON_ALREADY_ISSUED)
        }
        return ClaimedCoupon(requireNotNull(coupon.id), coupon.expiresAt)
    }

    /**
     * 주문 금액 미리보기용 조회(락·UPDATE 없음). 없거나 남의 쿠폰이면 `COUPON_NOT_FOUND`, 사용됐거나 만료면 `COUPON_NOT_AVAILABLE`.
     * 정책이 종료됐어도 쿠폰이 만료 전이면 쓸 수 있다(스펙 4-1).
     */
    @Transactional(readOnly = true)
    fun quoteCoupons(memberId: Long, couponIds: Collection<Long>): List<CouponTermsResult> {
        val now = LocalDateTime.now(clock)
        val coupons = ownedCoupons(memberId, couponIds)
        if (coupons.any { it.status != CouponStatus.AVAILABLE || it.isExpired(now) }) throw DomainException(ErrorCode.COUPON_NOT_AVAILABLE)
        return termsOf(coupons)
    }

    /**
     * 쿠폰을 `AVAILABLE → USED` 로 바꾼다(조건부 UPDATE, 쿠폰 ID 오름차순). 영향 행이 0 이면 남의 것·없는 쿠폰은 `COUPON_NOT_FOUND`,
     * 그 밖(사용됨·만료·동시 사용 경합)은 `COUPON_NOT_AVAILABLE`. 주문 생성 트랜잭션 안에서 `member_point` 락 뒤에 불려야 한다(ADR 0004).
     */
    @Transactional
    fun reserveCoupons(memberId: Long, couponIds: Collection<Long>): List<CouponTermsResult> {
        val now = LocalDateTime.now(clock)
        for (couponId in couponIds.toSortedSet()) {
            if (!couponRepository.markUsed(couponId, memberId, now)) {
                val coupon = couponRepository.findById(couponId)
                if (coupon == null || coupon.memberId != memberId) throw DomainException(ErrorCode.COUPON_NOT_FOUND)
                throw DomainException(ErrorCode.COUPON_NOT_AVAILABLE)
            }
        }
        return termsOf(ownedCoupons(memberId, couponIds))
    }

    /** 주문 저장 뒤 `USED` 쿠폰에 주문 ID 를 기록한다. */
    @Transactional
    fun attachOrder(couponIds: Collection<Long>, orderId: Long) {
        couponRepository.attachOrder(couponIds, orderId)
    }

    /**
     * 주문이 닫히거나 항목이 취소될 때 쿠폰을 `USED → AVAILABLE` 로 돌린다(조건부 UPDATE, 쿠폰 ID 오름차순). 만료된 쿠폰은 돌리지 않는다.
     * 호출 트랜잭션(주문 행 락 → member_point 락 뒤)에 참여한다. 돌린 쿠폰 ID 를 돌려준다.
     */
    @Transactional
    fun restoreCoupons(couponIds: Collection<Long>): List<Long> {
        val now = LocalDateTime.now(clock)
        return couponIds.toSortedSet().filter { couponRepository.restoreIfNotExpired(it, now) }
    }

    private fun ownedCoupons(memberId: Long, couponIds: Collection<Long>): List<Coupon> {
        if (couponIds.isEmpty()) return emptyList()
        val coupons = couponRepository.findAllByIds(couponIds.toSet())
        if (coupons.size != couponIds.toSet().size || coupons.any { it.memberId != memberId }) {
            throw DomainException(ErrorCode.COUPON_NOT_FOUND)
        }
        return coupons
    }

    private fun termsOf(coupons: List<Coupon>): List<CouponTermsResult> {
        val policies = policyRepository.findAllByIds(coupons.map { it.policyId }.toSet()).associateBy { it.id }
        return coupons.map { coupon ->
            val policy = checkNotNull(policies[coupon.policyId]) { "쿠폰 정책이 없다: policyId=${coupon.policyId}" }
            CouponTermsResult(
                couponId = requireNotNull(coupon.id), policyId = coupon.policyId, couponType = policy.couponType.name,
                issuerType = policy.issuerType.name, targetType = policy.targetType.name, targetProductId = policy.targetProductId,
                sellerId = policy.sellerId, discountType = policy.discountType.name, discountValue = policy.discountValue,
                maxDiscount = policy.maxDiscount, minOrderAmount = policy.minOrderAmount,
            )
        }
    }
}
